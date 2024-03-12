package com.spotify.confidence.eventsender;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

class EventSenderEngineImpl implements EventSenderEngine {
  private final ExecutorService writeThread = Executors.newSingleThreadExecutor();
  private final ExecutorService uploadThread = Executors.newSingleThreadExecutor();
  private final BlockingQueue<Event> writeQueue = new LinkedBlockingQueue<>();
  private final BlockingQueue<Object> uploadQueue = new LinkedBlockingQueue<>();
  private final EventSenderStorage eventStorage = new InMemoryStorage();
  private final List<FlushPolicy> flushPolicies;
  private boolean isStopped = false;

  public EventSenderEngineImpl(List<FlushPolicy> flushPolicyList) {
    this.flushPolicies = flushPolicyList;
    writeThread.submit(new WritePoller());
    uploadThread.submit(new UploadPoller());
  }

  class WritePoller implements Runnable {
    @Override
    public void run() {
      while (!isStopped) {
        try {
          Event event = writeQueue.take();
          eventStorage.write(event);
          flushPolicies.forEach(FlushPolicy::hit);

          if (flushPolicies.stream().anyMatch(FlushPolicy::shouldFlush)) {
            flushPolicies.forEach(FlushPolicy::reset);
            eventStorage.createBatch();
            uploadQueue.add(new Object());
          }
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  class UploadPoller implements Runnable {
    private final EventUploader uploader = new EventUploader() {
      @Override
      public CompletableFuture<Boolean> upload(EventBatch batch) {
        System.out.println(
                "Sending batch: "
                        + batch.events().stream().map(e -> e.name()).collect(Collectors.toList()));
        return null;
      }

      @Override
      public void close() {

      }
    };

    @Override
    public void run() {
      while (!isStopped) {
        try {
          uploadQueue.take();
          List<EventBatch> batches = List.copyOf(eventStorage.getBatches());
          if (batches.isEmpty()) {
            continue;
          }
          for (EventBatch batch : batches) {
            boolean uploadSuccessful = uploader.upload(batch).get();
            if (uploadSuccessful) {
              eventStorage.deleteBatch(batch.id());
            }
          }
        } catch (InterruptedException | ExecutionException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  @Override
  public void send(String name, ConfidenceValue.Struct context) {
    send(name, ConfidenceValue.of(ImmutableMap.of()), context);
  }

  @Override
  public void send(String name, ConfidenceValue.Struct message, ConfidenceValue.Struct context) {
    writeQueue.add(new Event(name, message, context));
  }

  @Override
  public void close() {
    // Prevent runnables from waiting on the blocking queues
    isStopped = true;
    // Trigger the runnables one more time if waiting on blocking queues
    uploadQueue.add(new Object());
    writeThread.shutdown();
    uploadThread.shutdown();
    try {
      boolean isUploadTerminated = uploadThread.awaitTermination(10, TimeUnit.SECONDS);
      System.out.printf(
          "Termination complete, upload thread correct termination %S%n", isUploadTerminated);
    } catch (InterruptedException e) {
      System.out.println(e.getMessage());
    }
  }
}
