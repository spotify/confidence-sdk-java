package com.spotify.confidence.flags.resolver;

import static com.spotify.confidence.flags.resolver.ResolveTokenConverter.toAssignedFlag;

import com.google.common.base.Stopwatch;
import com.google.protobuf.ByteString;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.spotify.confidence.flags.resolver.domain.AccountClient;
import com.spotify.confidence.flags.resolver.domain.AccountState;
import com.spotify.confidence.flags.resolver.domain.AssignmentMatch;
import com.spotify.confidence.flags.resolver.domain.FlagToApply;
import com.spotify.confidence.flags.resolver.domain.ResolvedValue;
import com.spotify.confidence.flags.resolver.exceptions.BadRequestException;
import com.spotify.confidence.flags.resolver.materialization.MaterializationStore;
import com.spotify.confidence.flags.resolver.v1.AppliedFlag;
import com.spotify.confidence.flags.resolver.v1.ApplyFlagsRequest;
import com.spotify.confidence.flags.resolver.v1.ResolveFlagsRequest;
import com.spotify.confidence.flags.resolver.v1.ResolveFlagsResponse;
import com.spotify.confidence.flags.resolver.v1.ResolveReason;
import com.spotify.confidence.flags.resolver.v1.ResolveTokenV1;
import com.spotify.confidence.flags.resolver.v1.ResolvedFlag;
import com.spotify.confidence.flags.resolver.v1.Sdk;
import com.spotify.confidence.flags.types.v1.FlagSchema;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlagResolverService {
  private static final Logger logger = LoggerFactory.getLogger(FlagResolverService.class);
  private static final Logger LOG = LoggerFactory.getLogger(FlagResolverService.class);

  private final Supplier<String> resolveIdSupplier;
  private final Supplier<Instant> timeSupplier;
  private final AccountState accountState;
  private final AccountClient accountClient;
  private final FlagLogger flagLogger;
  private final ResolveTokenConverter resolveTokenConverter;
  private final Metrics metrics;
  private final MaterializationStore materializationStore;

  public FlagResolverService(
      AccountState accountState,
      AccountClient accountClient,
      FlagLogger flagLogger,
      ResolveTokenConverter resolveTokenConverter,
      Supplier<Instant> timeSupplier,
      Supplier<String> resolveIdSupplier,
      Metrics metrics,
      MaterializationStore materializationStore) {
    this.resolveIdSupplier = resolveIdSupplier;
    this.timeSupplier = timeSupplier;
    this.accountState = accountState;
    this.accountClient = accountClient;
    this.flagLogger = flagLogger;
    this.resolveTokenConverter = resolveTokenConverter;
    this.metrics = metrics;
    this.materializationStore = materializationStore;
  }

  public CompletableFuture<ResolveFlagsResponse> resolveFlags(ResolveFlagsRequest request) {
    final Instant now = timeSupplier.get();

    final AccountResolver resolver = getAccountResolver(request.getEvaluationContext());
    return resolver
        .resolveFlags(request.getFlagsList())
        .thenApply(
            resolvedValues -> {
              final String resolveId = generateResolveId();
              final ResolveFlagsResponse.Builder responseBuilder =
                  ResolveFlagsResponse.newBuilder()
                      .setResolveToken(ByteString.EMPTY)
                      .setResolveId(resolveId);

              resolvedValues.stream()
                  .map(this::toResolvedFlag)
                  .forEach(responseBuilder::addResolvedFlags);

              if (request.getApply()) {
                if (!resolvedValues.isEmpty()) {
                  flagLogger.logAssigns(
                      resolveId,
                      request.getSdk(),
                      toFlagsToApply(resolvedValues, now),
                      resolver.getClient());
                }
              } else {
                final ByteString resolveToken =
                    resolveTokenConverter.createResolveToken(
                        accountState.account().name(),
                        resolveId,
                        resolvedValues,
                        resolver.getEvaluationContext());
                responseBuilder.setResolveToken(resolveToken);
                metrics.markResolveTokenSize(resolveToken.size());
              }
              try {
                // We always send a FlagResolved event, even if no flags were resolved, for billing
                flagLogger.logResolve(
                    resolveId,
                    resolver.getEvaluationContext(),
                    request.getSdk(),
                    resolver.getClient(),
                    resolvedValues);
              } catch (Exception ex) {
                logger.warn("Could not send to pubsub", ex);
              }
              return responseBuilder.build();
            });
  }

  private List<FlagToApply> toFlagsToApply(List<ResolvedValue> resolvedValues, Instant now) {
    return resolvedValues.stream()
        .map(resolvedValue -> new FlagToApply(now, toAssignedFlag(resolvedValue)))
        .toList();
  }

  public void applyFlags(ApplyFlagsRequest request) {
    if (!request.hasSendTime()) {
      throw new BadRequestException("Missing send time in request");
    }
    if (request.getFlagsCount() <= 0) {
      logger.info(
          "Empty apply flags request from account {} and client {}, dropping",
          accountState.account().name(),
          accountClient.client().getName());
      return;
    }

    final Instant sendTime = toInstant(request.getSendTime());
    final Instant receiveTime = timeSupplier.get();

    final ResolveTokenV1 token =
        resolveTokenConverter.readResolveToken(request.getResolveToken()).getTokenV1();
    if (!token.getAccount().isBlank()
        && !token.getAccount().equals(accountState.account().name())) {
      metrics.markAccountMismatch(accountState.account().name());
      return;
    }

    // Ensure that all flags are present before we start sending events
    final Map<String, ResolveTokenV1.AssignedFlag> assignments = token.getAssignmentsMap();
    request
        .getFlagsList()
        .forEach(
            appliedFlag -> {
              if (!assignments.containsKey(appliedFlag.getFlag())) {
                throw new BadRequestException(
                    "Flag in resolve token does not match flag in request");
              }
              if (!appliedFlag.hasApplyTime()) {
                throw new BadRequestException(
                    "Missing apply time for flag %s".formatted(appliedFlag.getFlag()));
              }
            });
    final Stopwatch stopwatch = Stopwatch.createStarted();
    final long distinctFlagsCount =
        request.getFlagsList().stream().map(AppliedFlag::getFlag).distinct().count();
    if (request.getFlagsCount() != distinctFlagsCount) {
      logger.info(
          "Request for {} using sdk {} {} contained duplicated flags. Total {}, unique {}",
          request.getSdk().getSdkCase() == Sdk.SdkCase.CUSTOM_ID
              ? request.getSdk().getCustomId()
              : request.getSdk().getId().name(),
          request.getSdk().getVersion(),
          accountState.account().name(),
          request.getFlagsCount(),
          distinctFlagsCount);

      throw new BadRequestException(
          "Request contained duplicate flags. Each flag must only be applied once per request");
    }

    final List<FlagToApply> flagsToApply =
        request.getFlagsList().stream()
            .map(
                appliedFlag -> {
                  final Instant applyTime = toInstant(appliedFlag.getApplyTime());
                  final Duration skew = Duration.between(applyTime, sendTime);
                  final Instant skewAdjustedAppliedTime = receiveTime.minus(skew);

                  return new FlagToApply(
                      skewAdjustedAppliedTime, assignments.get(appliedFlag.getFlag()));
                })
            .toList();

    try {
      flagLogger.logAssigns(token.getResolveId(), request.getSdk(), flagsToApply, accountClient);
    } catch (Exception ex) {
      logger.warn("Could not send to pubsub", ex);
    }
    metrics.markFlagApplyTime(stopwatch.elapsed());
    metrics.markAppliedFlagCount(request.getFlagsCount());
  }

  private ResolvedFlag toResolvedFlag(ResolvedValue resolvedValue) {
    final var builder =
        ResolvedFlag.newBuilder()
            .setFlag(resolvedValue.flag().getName())
            .setReason(resolvedValue.reason());

    if (resolvedValue.reason() == ResolveReason.RESOLVE_REASON_MATCH) {
      builder.setShouldApply(true);
    } else {
      builder.setShouldApply(!resolvedValue.fallthroughRules().isEmpty());
    }

    if (resolvedValue.matchedAssignment().isEmpty()) {
      return builder.build();
    }

    final AssignmentMatch match = resolvedValue.matchedAssignment().get();

    return builder
        .setVariant(match.variant().orElse(""))
        .setValue(match.value().orElse(Struct.getDefaultInstance()))
        .setFlagSchema(
            match.value().isPresent()
                ? resolvedValue.flag().getSchema()
                : FlagSchema.StructFlagSchema.getDefaultInstance())
        .build();
  }

  private AccountResolver getAccountResolver(Struct evaluationContext) {
    return new AccountResolver(
        materializationStore, accountClient, accountState, evaluationContext, LOG, metrics);
  }

  private String generateResolveId() {
    return resolveIdSupplier.get();
  }

  private Instant toInstant(Timestamp timestamp) {
    return Instant.ofEpochSecond(timestamp.getSeconds(), (long) timestamp.getNanos());
  }
}
