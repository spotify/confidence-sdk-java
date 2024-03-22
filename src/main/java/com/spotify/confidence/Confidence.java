package com.spotify.confidence;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Beta
public class Confidence implements EventSender, Contextual {

  private Map<String, ConfidenceValue> context = Maps.newHashMap();

  private final Confidence parent;

  private final EventSenderEngine eventSenderEngine;
  private final FlagResolverClient flagResolverClient;

  Confidence(
      @Nullable Confidence parent,
      EventSenderEngine eventSenderEngine,
      FlagResolverClient flagResolverClient) {
    this.parent = parent;
    this.eventSenderEngine = eventSenderEngine;
    this.flagResolverClient = flagResolverClient;
  }

  private Stream<Map.Entry<String, ConfidenceValue>> contextEntries(Confidence confidence) {
    // We shouldn't need to create this stream every time.
    final Stream.Builder<Map.Entry<String, ConfidenceValue>> streamBuilder = Stream.builder();
    if (confidence.parent != null) {
      contextEntries(confidence.parent)
          .filter((entry) -> !context.containsKey(entry.getKey()))
          .forEach(streamBuilder::add);
    }
    confidence.context.entrySet().forEach(streamBuilder::add);
    return streamBuilder.build();
  }

  @Override
  public ConfidenceValue.Struct getContext() {
    return ConfidenceValue.of(
        contextEntries(this)
            .filter((entry) -> !entry.getValue().isNull())
            .collect(Collectors.toConcurrentMap(Map.Entry::getKey, Map.Entry::getValue)));
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
  public Confidence withContext(Map<String, ConfidenceValue> context) {
    return withContext(ConfidenceValue.Struct.of(context));
  }

  @Override
  public Confidence withContext(ConfidenceValue.Struct context) {
    final Confidence child = new Confidence(this, eventSenderEngine, flagResolverClient);
    child.setContext(context);
    return child;
  }

  @Override
  public void send(String name) {
    eventSenderEngine.send(name, getContext(), Optional.empty());
  }

  @Override
  public void send(String name, ConfidenceValue.Struct message) {
    eventSenderEngine.send(name, getContext(), Optional.of(message));
  }

  CompletableFuture<ResolveFlagsResponse> resolveFlags(String flagName) {
    return flagResolverClient.resolveFlags(flagName, getContext());
  }

  public void close() throws IOException {
    eventSenderEngine.close();
    flagResolverClient.close();
  }

  public static Confidence.Builder builder(String clientSecret) {
    return new Confidence.Builder(clientSecret);
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
      return new Confidence(null, engine, flagResolverClient);
    }
  }
}
