package com.spotify.confidence.eventsender;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.concurrent.*;

class EventSenderEngineImpl implements EventSenderEngine {
  private final ExecutorService writeThread = Executors.newSingleThreadExecutor();
  private final ExecutorService uploadThread = Executors.newSingleThreadExecutor();
  private final ConcurrentLinkedQueue<Event> writeQueue = new ConcurrentLinkedQueue<>();
  private final BlockingQueue<String> uploadQueue = new LinkedBlockingQueue<>();
  private final BlockingQueue<String> shutdownQueue = new LinkedBlockingQueue<>(1);
  private final EventSenderStorage eventStorage = new InMemoryStorage();
  private final EventUploader eventUploader;
  private final List<FlushPolicy> flushPolicies;
  private static final String UPLOAD_SIG = "UPLOAD";
  private static final String SHUTDOWN_UPLOAD = "SHUTDOWN_UPLOAD";
  private static final String SHUTDOWN_UPLOAD_COMPLETED = "SHUTDOWN_UPLOAD_COMPLETED";
  private static final String SHUTDOWN_WRITE_COMPLETED = "SHUTDOWN_WRITE_COMPLETED";
  private volatile boolean isStopped = false;

  public EventSenderEngineImpl(List<FlushPolicy> flushPolicyList, EventUploader eventUploader) {
    this.flushPolicies = flushPolicyList;
    this.eventUploader = eventUploader;
    writeThread.submit(new WritePoller());
    uploadThread.submit(new UploadPoller());
  }

  class WritePoller implements Runnable {
    @Override
    public void run() {
      while (true) {
        final Event event = writeQueue.poll();
        if (event == null && isStopped) {
          shutdownQueue.add(SHUTDOWN_WRITE_COMPLETED);
          break;
        } else if (event == null) {
          Thread.yield();
          continue;
        }
        eventStorage.write(event);
        flushPolicies.forEach(FlushPolicy::hit);

        if (flushPolicies.stream().anyMatch(FlushPolicy::shouldFlush)) {
          flushPolicies.forEach(FlushPolicy::reset);
          eventStorage.createBatch();
          uploadQueue.add(UPLOAD_SIG);
        }
      }
    }
  }

  class UploadPoller implements Runnable {
    @Override
    public void run() {
      while (true) {
        try {
          final String signal = uploadQueue.take();
          final List<EventBatch> batches = List.copyOf(eventStorage.getBatches());
          for (EventBatch batch : batches) {
            final boolean uploadSuccessful = eventUploader.upload(batch).get();
            if (uploadSuccessful) {
              eventStorage.deleteBatch(batch.id());
            }
          }
          if (signal.equals(SHUTDOWN_UPLOAD)) {
            shutdownQueue.add(SHUTDOWN_UPLOAD_COMPLETED);
          }
        } catch (InterruptedException | ExecutionException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  @Override
  public void send(String name, Value.Struct context) {
    send(name, Value.of(ImmutableMap.of()), context);
  }

  @Override
  public void send(String name, Value.Struct message, Value.Struct context) {
    if (!isStopped) {
      writeQueue.add(new Event(name, message, context));
    }
  }

  @Override
  public void close() {
    // stop accepting new events
    final ExecutorService thread = Executors.newSingleThreadExecutor();
    thread.submit(
        () -> {
          try {
            // wait until all the events in the queue are written
            final String writeQueueShutdown = shutdownQueue.take();
            assert (writeQueueShutdown.equals(SHUTDOWN_WRITE_COMPLETED));
            // create the final batch
            eventStorage.createBatch();
            uploadQueue.add(SHUTDOWN_UPLOAD);
            // wait until all the written events are uploaded
            final String uploadQueueShutdown = shutdownQueue.take();
            assert (uploadQueueShutdown.equals(SHUTDOWN_UPLOAD_COMPLETED));
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
          writeThread.shutdownNow();
          uploadThread.shutdownNow();
        });
    isStopped = true;

    thread.shutdown();

    try {
      thread.awaitTermination(20, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
