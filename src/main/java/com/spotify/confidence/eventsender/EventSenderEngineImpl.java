package com.spotify.confidence.eventsender;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.concurrent.*;

class EventSenderEngineImpl implements EventSenderEngine {
  private final ExecutorService writeThread = Executors.newSingleThreadExecutor();
  private final ExecutorService uploadThread = Executors.newSingleThreadExecutor();
  private final BlockingQueue<Event> writeQueue = new LinkedBlockingQueue<>();
  private final BlockingQueue<String> shutdownQueue = new LinkedBlockingQueue<>(1);
  private final BlockingQueue<String> uploadQueue = new LinkedBlockingQueue<>();
  private final EventSenderStorage eventStorage = new InMemoryStorage();
  private final EventUploader eventUploader;
  private final List<FlushPolicy> flushPolicies;
  private static final String UPLOAD_SIG = "UPLOAD";
  private static final String SHUTDOWN_UPLOAD = "SHUTDOWN_UPLOAD";
  private static final String SHUTDOWN_WRITE = "SHUTDOWN_WRITE";
  private boolean isStopped = false;

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
        try {
          Event event = writeQueue.take();
          eventStorage.write(event);
          flushPolicies.forEach(FlushPolicy::hit);

          if (flushPolicies.stream().anyMatch(FlushPolicy::shouldFlush)) {
            flushPolicies.forEach(FlushPolicy::reset);
            eventStorage.createBatch();
            uploadQueue.add(UPLOAD_SIG);
          }

          if (isStopped && writeQueue.isEmpty()) {
            shutdownQueue.add(SHUTDOWN_WRITE);
          }

        } catch (InterruptedException e) {
          throw new RuntimeException(e);
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
          List<EventBatch> batches = List.copyOf(eventStorage.getBatches());
          for (EventBatch batch : batches) {
            boolean uploadSuccessful = eventUploader.upload(batch).get();
            if (uploadSuccessful) {
              eventStorage.deleteBatch(batch.id());
            }
          }

          if (signal.equals(SHUTDOWN_UPLOAD)) {
            shutdownQueue.add(SHUTDOWN_UPLOAD);
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
    if (!isStopped) {
      writeQueue.add(new Event(name, message, context));
    }
  }

  @Override
  public void close() {
    // stop accepting new events
    isStopped = true;
    try {
      // wait until all the events in the queue are written
      shutdownQueue.take();
      eventStorage.createBatch();
      uploadQueue.add(SHUTDOWN_UPLOAD);
      // wait until all the written events are uploaded
      shutdownQueue.take();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    writeThread.shutdownNow();
    uploadThread.shutdownNow();
  }
}
