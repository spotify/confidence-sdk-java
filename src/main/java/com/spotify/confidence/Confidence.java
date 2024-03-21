package com.spotify.confidence;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ListenableFuture;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Beta
public class Confidence implements EventSender, Contextual {

  private Map<String, ConfidenceValue> context = Maps.newHashMap();

  private final Contextual parent;

  private final EventSenderEngine eventSenderEngine;
  private final FlagResolverClient flagResolverClient;

  Confidence(
      @Nullable Contextual parent,
      EventSenderEngine eventSenderEngine,
      FlagResolverClient flagResolverClient) {
    this.parent = parent;
    this.eventSenderEngine = eventSenderEngine;
    this.flagResolverClient = flagResolverClient;
  }

  @Override
  public ConfidenceValue.Struct getContext() {
    final ConfidenceValue.Struct parentContext =
        parent != null ? parent.getContext() : ConfidenceValue.Struct.EMPTY;

    final Map<String, ConfidenceValue> mergedContext = Maps.newHashMap();

    for (Map.Entry<String, ConfidenceValue> entry : parentContext.asMap().entrySet()) {
      if (!context.containsKey(entry.getKey())) {
        mergedContext.put(entry.getKey(), entry.getValue());
      }
    }
    context.entrySet().stream()
        .filter((entry) -> !entry.getValue().isNull())
        .forEach((entry) -> mergedContext.put(entry.getKey(), entry.getValue()));
    return ConfidenceValue.of(mergedContext);
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

  ListenableFuture<ResolveFlagsResponse> resolveFlags(String flagName) {
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
      final GrpcEventUploader uploader =
          new GrpcEventUploader(clientSecret, new SystemClock(), DEFAULT_CHANNEL);
      final List<FlushPolicy> flushPolicies = ImmutableList.of(new BatchSizeFlushPolicy(5));
      final EventSenderEngine engine = new EventSenderEngineImpl(flushPolicies, uploader);
      return new Confidence(null, engine, flagResolverClient);
    }
  }
}
