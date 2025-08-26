package com.spotify.confidence;

import com.google.protobuf.Struct;
import com.spotify.confidence.shaded.flags.admin.v1.FlagAdminServiceGrpc;
import com.spotify.confidence.shaded.flags.admin.v1.WriteResolveInfoRequest;
import java.io.Closeable;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ResolveLogger implements Closeable {
  private static final Logger logger = LoggerFactory.getLogger(ResolveLogger.class);
  private final Supplier<FlagAdminServiceGrpc.FlagAdminServiceBlockingStub> adminStub;

  private final AtomicReference<ResolveInfoState> stateRef =
      new AtomicReference<>(new ResolveInfoState());
  private final Timer timer;

  private ResolveLogger(
      Supplier<FlagAdminServiceGrpc.FlagAdminServiceBlockingStub> adminStub, Timer timer) {
    this.adminStub = adminStub;
    this.timer = timer;
  }

  static ResolveLogger createStarted(
      Supplier<FlagAdminServiceGrpc.FlagAdminServiceBlockingStub> adminStub,
      Duration checkpointInterval) {
    final Timer timer = new Timer("resolve-logger-timer", true);
    final ResolveLogger resolveLogger = new ResolveLogger(adminStub, timer);

    timer.scheduleAtFixedRate(
        new TimerTask() {
          @Override
          public void run() {
            try {
              resolveLogger.checkpoint();
            } catch (Exception ex) {
              logger.error("Could not checkpoint", ex);
            }
          }
        },
        checkpointInterval.toMillis(),
        checkpointInterval.toMillis());
    return resolveLogger;
  }

  private void checkpoint() {
    final var state = stateRef.getAndSet(new ResolveInfoState());
    final var lock = state.readWriteLock.writeLock();
    try {
      lock.lock();
      if (state.isEmpty()) {
        return;
      }
      adminStub
          .get()
          .writeResolveInfo(
              WriteResolveInfoRequest.newBuilder()
                  .addAllClientResolveInfo(
                      state.clientResolveInfo().entrySet().stream()
                          .map(
                              entry ->
                                  WriteResolveInfoRequest.ClientResolveInfo.newBuilder()
                                      .setClient(extractClient(entry.getKey()))
                                      .setClientCredential(entry.getKey())
                                      .addAllSchema(
                                          entry.getValue().schemas().stream()
                                              .map(
                                                  s ->
                                                      WriteResolveInfoRequest.ClientResolveInfo
                                                          .EvaluationContextSchemaInstance
                                                          .newBuilder()
                                                          .putAllSchema(s.fields())
                                                          .putAllSemanticTypes(s.semanticTypes())
                                                          .build())
                                              .toList())
                                      .build())
                          .toList())
                  .addAllFlagResolveInfo(
                      state.flagResolveInfo().entrySet().stream()
                          .map(
                              entry ->
                                  WriteResolveInfoRequest.FlagResolveInfo.newBuilder()
                                      .setFlag(entry.getKey())
                                      .addAllRuleResolveInfo(
                                          entry.getValue().ruleResolveInfo().entrySet().stream()
                                              .map(
                                                  ruleInfo ->
                                                      WriteResolveInfoRequest.FlagResolveInfo
                                                          .RuleResolveInfo.newBuilder()
                                                          .setRule(ruleInfo.getKey())
                                                          .setCount(
                                                              ruleInfo.getValue().count().get())
                                                          .addAllAssignmentResolveInfo(
                                                              ruleInfo
                                                                  .getValue()
                                                                  .assignmentCounts()
                                                                  .entrySet()
                                                                  .stream()
                                                                  .map(
                                                                      assignmentEntry ->
                                                                          WriteResolveInfoRequest
                                                                              .FlagResolveInfo
                                                                              .AssignmentResolveInfo
                                                                              .newBuilder()
                                                                              .setAssignmentId(
                                                                                  assignmentEntry
                                                                                      .getKey())
                                                                              .setCount(
                                                                                  assignmentEntry
                                                                                      .getValue()
                                                                                      .get())
                                                                              .build())
                                                                  .toList())
                                                          .build())
                                              .toList())
                                      .addAllVariantResolveInfo(
                                          entry.getValue().variantResolveInfo().entrySet().stream()
                                              .map(
                                                  variantInfo ->
                                                      WriteResolveInfoRequest.FlagResolveInfo
                                                          .VariantResolveInfo.newBuilder()
                                                          .setVariant(variantInfo.getKey())
                                                          .setCount(
                                                              variantInfo.getValue().count().get())
                                                          .build())
                                              .toList())
                                      .build())
                          .toList())
                  .build());

    } finally {
      lock.unlock();
    }
  }

  private String extractClient(String key) {
    final String[] split = key.split("/");
    return split[0] + "/" + split[1];
  }

  void logResolve(
      String resolveId,
      Struct evaluationContext,
      AccountClient accountClient,
      List<ResolvedValue> values) {
    ResolveInfoState state = stateRef.get();
    Lock lock = state.readWriteLock.readLock();
    try {
      if (!lock.tryLock()) {
        // If we failed to lock it means that the checkpoint is currently running, which means that
        // the state has just been reset, so we can take the new state and lock on that
        state = stateRef.get();
        lock = state.readWriteLock.readLock();
        lock.lock();
      }

      final SchemaFromEvaluationContext.DerivedClientSchema derivedSchema =
          SchemaFromEvaluationContext.getSchema(evaluationContext);
      state
          .clientResolveInfo(accountClient.clientCredential().getName())
          .schemas()
          .add(derivedSchema);

      for (ResolvedValue value : values) {
        final var flagState = state.flagResolveInfo(value.flag().getName());
        for (var fallthrough : value.fallthroughAssignments()) {
          flagState.ruleResolveInfo(fallthrough.getRule()).increment(fallthrough.getAssignmentId());
        }

        if (value.matchedAssignment().isEmpty()) {
          flagState.variantResolveInfo("").increment();
        } else {
          final var match = value.matchedAssignment().get();
          flagState.variantResolveInfo(match.variant().orElse("")).increment();
          flagState.ruleResolveInfo(match.matchedRule().getName()).increment(match.assignmentId());
        }
      }
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void close() {
    timer.cancel();
    checkpoint();
  }

  private record ResolveInfoState(
      ConcurrentMap<String, FlagResolveInfo> flagResolveInfo,
      ConcurrentMap<String, ClientResolveInfoState> clientResolveInfo,
      ReadWriteLock readWriteLock) {
    ResolveInfoState() {
      this(new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ReentrantReadWriteLock());
    }

    FlagResolveInfo flagResolveInfo(String flag) {
      return flagResolveInfo.computeIfAbsent(flag, ignore -> new FlagResolveInfo());
    }

    ClientResolveInfoState clientResolveInfo(String clientCredential) {
      return clientResolveInfo.computeIfAbsent(
          clientCredential, ignore -> new ClientResolveInfoState());
    }

    boolean isEmpty() {
      return flagResolveInfo.isEmpty() && clientResolveInfo.isEmpty();
    }
  }

  private record ClientResolveInfoState(
      Set<SchemaFromEvaluationContext.DerivedClientSchema> schemas) {
    ClientResolveInfoState() {
      this(ConcurrentHashMap.newKeySet());
    }
  }

  private record FlagResolveInfo(
      ConcurrentMap<String, VariantResolveInfo> variantResolveInfo,
      ConcurrentMap<String, RuleResolveInfo> ruleResolveInfo) {
    FlagResolveInfo() {
      this(new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
    }

    VariantResolveInfo variantResolveInfo(String s) {
      return variantResolveInfo.computeIfAbsent(
          s, ignore -> new VariantResolveInfo(new AtomicLong()));
    }

    RuleResolveInfo ruleResolveInfo(String s) {
      return ruleResolveInfo.computeIfAbsent(
          s, ignore -> new RuleResolveInfo(new AtomicLong(), new ConcurrentHashMap<>()));
    }
  }

  private record VariantResolveInfo(AtomicLong count) {
    void increment() {
      count.incrementAndGet();
    }
  }

  private record RuleResolveInfo(AtomicLong count, Map<String, AtomicLong> assignmentCounts) {
    void increment(String assignmentId) {
      count.incrementAndGet();
      assignmentCounts.computeIfAbsent(assignmentId, a -> new AtomicLong()).incrementAndGet();
    }
  }
}
