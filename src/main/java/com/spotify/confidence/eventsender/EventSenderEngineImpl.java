package com.spotify.confidence.eventsender;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

class EventSenderEngineImpl implements EventSenderEngine {
  private final ExecutorService writeThread = Executors.newSingleThreadExecutor();
  private final ExecutorService uploadThread = Executors.newSingleThreadExecutor();
  private final BlockingQueue<Event> writeQueue = new LinkedBlockingQueue<>();
  private final BlockingQueue<Object> uploadQueue = new LinkedBlockingQueue<>();
  private final EventSenderStorage eventStorage = new InMemoryStorage();
  private final EventUploader eventUploader;
  private final List<FlushPolicy> flushPolicies;

  private final AtomicBoolean isStopped = new AtomicBoolean(false);
  private final AtomicBoolean writerStopped = new AtomicBoolean(false);

  public EventSenderEngineImpl(List<FlushPolicy> flushPolicyList, EventUploader eventUploader) {
    this.flushPolicies = flushPolicyList;
    this.eventUploader = eventUploader;
    writeThread.submit(new WritePoller());
    uploadThread.submit(new UploadPoller());
  }

  class WritePoller implements Runnable {
    @Override
    public void run() {
      while (!isStopped.get() || (isStopped.get() && !writeQueue.isEmpty())) {
        try {
          Event event = writeQueue.poll(5, TimeUnit.SECONDS);
          if (event == null) {
            continue;
          }
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
      // Make remaining events ready for upload, then trigger a new upload signal
      eventStorage.createBatch();
      uploadQueue.add(new Object());
      writerStopped.set(true);
    }
  }

  class UploadPoller implements Runnable {
    @Override
    public void run() {
      while (!writerStopped.get() || (writerStopped.get() && !uploadQueue.isEmpty())) {
        try {
          uploadQueue.take();
          List<EventBatch> batches = List.copyOf(eventStorage.getBatches());
          if (batches.isEmpty()) {
            continue;
          }
          for (EventBatch batch : batches) {
            boolean uploadSuccessful = eventUploader.upload(batch).get();
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
    if (!isStopped.get()) {
      writeQueue.add(new Event(name, message, context));
    } else {
      System.out.println("The EventSender is closed");
    }
  }

  @Override
  public void close() {
    isStopped.set(true);
    writeThread.shutdown();
    uploadThread.shutdown();
    try {
      boolean isUploadTerminated = uploadThread.awaitTermination(10, TimeUnit.SECONDS);
      boolean isWriteTerminated = writeThread.awaitTermination(10, TimeUnit.SECONDS);
      System.out.printf(
          "Termination complete. Writes completed: %S, uploads completed: %S%n",
          isWriteTerminated, isUploadTerminated);
    } catch (InterruptedException e) {
      System.out.println(e.getMessage());
    }
  }
}
