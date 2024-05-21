package com.spotify.confidence;

import static com.spotify.confidence.ConfidenceTypeMapper.getTyped;
import static com.spotify.confidence.ConfidenceUtils.FlagPath.getPath;
import static com.spotify.confidence.ConfidenceUtils.getValueForPath;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.io.Closer;
import com.spotify.confidence.ConfidenceExceptions.IllegalValuePath;
import com.spotify.confidence.ConfidenceExceptions.IllegalValueType;
import com.spotify.confidence.ConfidenceExceptions.IncompatibleValueType;
import com.spotify.confidence.ConfidenceExceptions.ValueNotFound;
import com.spotify.confidence.ConfidenceUtils.FlagPath;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolvedFlag;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collector;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;

@Beta
public abstract class Confidence implements EventSender, Closeable {

  protected Map<String, ConfidenceValue> context = Maps.newHashMap();
  private static final Logger log = org.slf4j.LoggerFactory.getLogger(Confidence.class);

  private Confidence() {}

  protected abstract ClientDelegate client();

  protected Stream<Map.Entry<String, ConfidenceValue>> contextEntries() {
    return context.entrySet().stream().filter(e -> !e.getValue().isNull());
  }

  @Override
  public ConfidenceValue.Struct getContext() {

    return contextEntries()
        .collect(
            Collector.of(
                ImmutableMap.Builder<String, ConfidenceValue>::new,
                ImmutableMap.Builder::put,
                (b1, b2) -> b1.putAll(b2.build()),
                builder -> ConfidenceValue.Struct.of(builder.build())));
  }

  @Override
  public void setContext(ConfidenceValue.Struct context) {
    this.context = Maps.newHashMap(context.asMap());
  }

  @Override
  public void updateContextEntry(String key, ConfidenceValue value) {
    this.context.put(key, value);
  }

  @Override
  public void removeContextEntry(String key) {
    this.context.put(key, ConfidenceValue.NULL_VALUE);
  }

  @Override
  public void clearContext() {
    this.context.clear();
  }

  @Override
  public Confidence withContext(ConfidenceValue.Struct context) {
    final Confidence child = new ChildInstance(this);
    child.setContext(context);
    return child;
  }

  @Override
  public Confidence withContext(Map<String, ConfidenceValue> context) {
    return this.withContext(ConfidenceValue.of(context));
  }

  @Override
  public void track(String eventName) {
    try {
      client().emit(eventName, getContext(), Optional.empty());
    } catch (IllegalStateException e) {
      // swallow this exception
    }
  }

  @Override
  public void track(String eventName, ConfidenceValue.Struct message) {
    try {
      client().emit(eventName, getContext(), Optional.of(message));
    } catch (IllegalStateException e) {
      // swallow this exception
    }
  }

  public <T> T getValue(String key, T defaultValue) {
    return getEvaluation(key, defaultValue).getValue();
  }

  public <T> FlagEvaluation<T> getEvaluation(String key, T defaultValue) {
    try {
      final FlagPath flagPath = getPath(key);
      final String requestFlagName = "flags/" + flagPath.getFlag();
      final ResolveFlagsResponse response = resolveFlags(requestFlagName).get();
      if (response.getResolvedFlagsList().isEmpty()) {
        final String errorMessage =
            String.format("No active flag '%s' was found", flagPath.getFlag());
        log.warn(errorMessage);
        return new FlagEvaluation<>(
            defaultValue, "", "ERROR", ErrorType.FLAG_NOT_FOUND, errorMessage);
      }

      final ResolvedFlag resolvedFlag = response.getResolvedFlags(0);
      if (!requestFlagName.equals(resolvedFlag.getFlag())) {
        final String errorMessage =
            String.format(
                "Unexpected flag '%s' from remote",
                resolvedFlag.getFlag().replaceFirst("^flags/", ""));
        log.warn(errorMessage);
        return new FlagEvaluation<>(
            defaultValue, "", "ERROR", ErrorType.INTERNAL_ERROR, errorMessage);
      }
      if (resolvedFlag.getVariant().isEmpty()) {
        final String errorMessage =
            String.format(
                "The server returned no assignment for the flag '%s'. Typically, this happens "
                    + "if no configured rules matches the given evaluation context.",
                flagPath.getFlag());
        log.debug(errorMessage);
        return new FlagEvaluation<>(defaultValue, "", resolvedFlag.getReason().toString());
      } else {
        final ConfidenceValue confidenceValue;
        confidenceValue =
            getValueForPath(
                flagPath.getPath(),
                ConfidenceTypeMapper.from(resolvedFlag.getValue(), resolvedFlag.getFlagSchema()));

        // regular resolve was successful
        return new FlagEvaluation<>(
            getTyped(confidenceValue, defaultValue),
            resolvedFlag.getVariant(),
            resolvedFlag.getReason().toString());
      }
    } catch (IllegalValuePath | ValueNotFound e) {
      log.warn(e.getMessage());
      return new FlagEvaluation<>(
          defaultValue, "", "ERROR", ErrorType.INVALID_VALUE_PATH, e.getMessage());
    } catch (IncompatibleValueType | IllegalValueType e) {
      log.warn(e.getMessage());
      return new FlagEvaluation<>(
          defaultValue, "", "ERROR", ErrorType.INVALID_VALUE_TYPE, e.getMessage());
    } catch (Exception e) {
      // catch all for any runtime exception
      log.warn(e.getMessage());
      return new FlagEvaluation<>(
          defaultValue, "", "ERROR", ErrorType.INTERNAL_ERROR, e.getMessage());
    }
  }

  CompletableFuture<ResolveFlagsResponse> resolveFlags(String flagName) {
    return client().resolveFlags(flagName, getContext());
  }

  @VisibleForTesting
  static Confidence create(
      EventSenderEngine eventSenderEngine, FlagResolverClient flagResolverClient) {
    final Closer closer = Closer.create();
    closer.register(eventSenderEngine);
    closer.register(flagResolverClient);
    return new RootInstance(new ClientDelegate(closer, flagResolverClient, eventSenderEngine));
  }

  public static Confidence.Builder builder(String clientSecret) {
    return new Confidence.Builder(clientSecret);
  }

  private static class ClientDelegate implements FlagResolverClient, EventSenderEngine {
    private final Closeable closeable;
    private final FlagResolverClient flagResolverClient;
    private final EventSenderEngine eventSenderEngine;

    private ClientDelegate(
        Closeable closeable,
        FlagResolverClient flagResolverClient,
        EventSenderEngine eventSenderEngine) {
      this.closeable = closeable;
      this.flagResolverClient = flagResolverClient;
      this.eventSenderEngine = eventSenderEngine;
    }

    @Override
    public void emit(
        String name, ConfidenceValue.Struct context, Optional<ConfidenceValue.Struct> message) {
      this.eventSenderEngine.emit(name, context, message);
    }

    @Override
    public CompletableFuture<ResolveFlagsResponse> resolveFlags(
        String flag, ConfidenceValue.Struct context) {
      return flagResolverClient.resolveFlags(flag, context);
    }

    @Override
    public void close() throws IOException {
      closeable.close();
    }
  }

  private static class ChildInstance extends Confidence {

    private final Confidence parent;
    private boolean closed = false;

    private ChildInstance(Confidence parent) {
      this.parent = parent;
    }

    @Override
    protected Stream<Map.Entry<String, ConfidenceValue>> contextEntries() {
      final Set<String> ownKeys = context.keySet();
      return Stream.concat(
          parent.contextEntries().filter(entry -> !ownKeys.contains(entry.getKey())),
          super.contextEntries());
    }

    @Override
    protected ClientDelegate client() {
      if (closed) throw new IllegalStateException("Resource closed");
      return parent.client();
    }

    @Override
    public void close() throws IOException {
      closed = true;
    }
  }

  private static class RootInstance extends Confidence {
    @Nullable private ClientDelegate client;

    private RootInstance(ClientDelegate client) {
      this.client = client;
    }

    @Override
    protected ClientDelegate client() {
      if (client == null) throw new IllegalStateException("Resource closed");
      return client;
    }

    @Override
    public void close() throws IOException {
      try {
        client().close();
      } finally {
        client = null;
      }
    }
  }

  public static class Builder {
    private final String clientSecret;
    private final Closer closer = Closer.create();

    private final ManagedChannel DEFAULT_CHANNEL =
        ManagedChannelBuilder.forAddress("edge-grpc.spotify.com", 443)
            .keepAliveTime(Duration.ofMinutes(5).getSeconds(), TimeUnit.SECONDS)
            .build();
    private ManagedChannel flagResolverManagedChannel = DEFAULT_CHANNEL;

    public Builder(@Nonnull String clientSecret) {
      this.clientSecret = clientSecret;
      registerChannelForShutdown(DEFAULT_CHANNEL);
    }

    public Builder flagResolverManagedChannel(String host, int port) {
      this.flagResolverManagedChannel =
          ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
      registerChannelForShutdown(this.flagResolverManagedChannel);
      return this;
    }

    public Builder flagResolverManagedChannel(ManagedChannel managedChannel) {
      this.flagResolverManagedChannel = managedChannel;
      return this;
    }

    public Confidence build() {
      final FlagResolverClient flagResolverClient =
          new FlagResolverClientImpl(
              new GrpcFlagResolver(clientSecret, flagResolverManagedChannel));
      final EventSenderEngine eventSenderEngine =
          new EventSenderEngineImpl(clientSecret, DEFAULT_CHANNEL, Instant::now);
      closer.register(flagResolverClient);
      closer.register(eventSenderEngine);
      return new RootInstance(new ClientDelegate(closer, flagResolverClient, eventSenderEngine));
    }

    private void registerChannelForShutdown(ManagedChannel channel) {
      this.closer.register(
          () -> {
            channel.shutdown();
            try {
              channel.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              channel.shutdownNow();
            }
          });
    }
  }
}
