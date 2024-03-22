package com.spotify.confidence;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.io.Closer;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collector;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Beta
public abstract class Confidence implements EventSender, Closeable {

  protected Map<String, ConfidenceValue> context = Maps.newHashMap();

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
  public void send(String name) {
    try {
      client().send(name, getContext(), Optional.empty());
    } catch (IllegalStateException e) {
      // swallow this exception
    }
  }

  @Override
  public void send(String name, ConfidenceValue.Struct message) {
    try {
      client().send(name, getContext(), Optional.of(message));
    } catch (IllegalStateException e) {
      // swallow this exception
    }
  }

  CompletableFuture<ResolveFlagsResponse> resolveFlags(String flagName) {
    return client().resolveFlags(flagName, getContext());
  }

  static Confidence create(
      EventSenderEngine eventSenderEngine, FlagResolverClient flagResolverClient) {
    return new RootInstance(new ClientDelegate(flagResolverClient, eventSenderEngine));
  }

  public static Confidence.Builder builder(String clientSecret) {
    return new Confidence.Builder(clientSecret);
  }

  private static class ClientDelegate implements FlagResolverClient, EventSenderEngine {
    private final Closer closer = Closer.create();
    private final FlagResolverClient flagResolverClient;
    private final EventSenderEngine eventSenderEngine;

    private ClientDelegate(
        FlagResolverClient flagResolverClient, EventSenderEngine eventSenderEngine) {
      this.flagResolverClient = closer.register(flagResolverClient);
      this.eventSenderEngine = closer.register(eventSenderEngine);
    }

    @Override
    public void send(
        String name, ConfidenceValue.Struct context, Optional<ConfidenceValue.Struct> message) {
      this.eventSenderEngine.send(name, context, message);
    }

    @Override
    public CompletableFuture<ResolveFlagsResponse> resolveFlags(
        String flag, ConfidenceValue.Struct context) {
      return flagResolverClient.resolveFlags(flag, context);
    }

    @Override
    public void close() throws IOException {
      closer.close();
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

    private final ManagedChannel DEFAULT_CHANNEL =
        ManagedChannelBuilder.forAddress("edge-grpc.spotify.com", 443).build();
    private ManagedChannel flagResolverManagedChannel = DEFAULT_CHANNEL;

    public Builder(@Nonnull String clientSecret) {
      this.clientSecret = clientSecret;
    }

    public Builder flagResolverManagedChannel(String host, int port) {
      this.flagResolverManagedChannel =
          ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
      return this;
    }

    public Builder flagResolverManagedChannel(ManagedChannel managedChannel) {
      this.flagResolverManagedChannel = managedChannel;
      return this;
    }

    public Confidence build() {
      final FlagResolverClient flagResolverClient =
          new FlagResolverClientImpl(clientSecret, flagResolverManagedChannel);
      final SystemClock clock = new SystemClock();
      final GrpcEventUploader uploader =
          new GrpcEventUploader(clientSecret, clock, DEFAULT_CHANNEL);
      final List<FlushPolicy> flushPolicies = ImmutableList.of(new BatchSizeFlushPolicy(5));
      final EventSenderEngine engine = new EventSenderEngineImpl(flushPolicies, uploader, clock);
      return Confidence.create(engine, flagResolverClient);
    }
  }
}
