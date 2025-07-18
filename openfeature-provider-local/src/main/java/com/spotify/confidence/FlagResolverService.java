package com.spotify.confidence;

import static com.spotify.confidence.ResolveTokenConverter.toAssignedFlag;

import com.google.protobuf.ByteString;
import com.google.protobuf.Struct;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsRequest;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolvedFlag;
import com.spotify.confidence.shaded.flags.types.v1.FlagSchema;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class FlagResolverService {
  private static final Logger logger = LoggerFactory.getLogger(FlagResolverService.class);
  private static final Logger LOG = LoggerFactory.getLogger(FlagResolverService.class);
  private final Supplier<String> resolveIdSupplier;
  private final Supplier<Instant> timeSupplier;
  private final AccountState accountState;
  private final AccountClient accountClient;
  private final FlagLogger flagLogger;
  private final ResolveTokenConverter resolveTokenConverter;

  FlagResolverService(
      AccountState accountState,
      AccountClient accountClient,
      FlagLogger flagLogger,
      ResolveTokenConverter resolveTokenConverter,
      Supplier<Instant> timeSupplier,
      Supplier<String> resolveIdSupplier) {
    this.resolveIdSupplier = resolveIdSupplier;
    this.timeSupplier = timeSupplier;
    this.accountState = accountState;
    this.accountClient = accountClient;
    this.flagLogger = flagLogger;
    this.resolveTokenConverter = resolveTokenConverter;
  }

  CompletableFuture<ResolveFlagsResponse> resolveFlags(ResolveFlagsRequest request) {
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

  private ResolvedFlag toResolvedFlag(ResolvedValue resolvedValue) {
    final var builder =
        ResolvedFlag.newBuilder()
            .setFlag(resolvedValue.flag().getName())
            .setReason(resolvedValue.reason());

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
    return new AccountResolver(accountClient, accountState, evaluationContext, LOG);
  }

  private String generateResolveId() {
    return resolveIdSupplier.get();
  }
}
