package com.spotify.confidence.eventsender;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.concurrent.*;

class EventSenderEngineImpl implements EventSenderEngine {
  private final ExecutorService writeThread = Executors.newSingleThreadExecutor();
  private final ExecutorService uploadThread = Executors.newSingleThreadExecutor();
  private final BlockingQueue<Event> writeQueue = new LinkedBlockingQueue<>();
  private final BlockingQueue<String> uploadQueue = new LinkedBlockingQueue<>();
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
            eventStorage.batch();
            System.out.println("UPLOAD SIGNAL");
            uploadQueue.add("Upload");
          }
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  class UploadPoller implements Runnable {
    private final EventUploader uploader =
        event -> {
          System.out.println("Event uploader -> batch -> " + event.events());
          return CompletableFuture.completedFuture(true);
        };

    @Override
    public void run() {
      while (!isStopped) {
        try {
          System.out.println("UPLOAD SIGNAL Waiting");
          uploadQueue.take();
          System.out.println("UPLOAD SIGNAL COMING");
          List<EventBatch> batches = List.copyOf(eventStorage.readyEvents());
          // for each batch upload -> boolean should clean up ->
          for (EventBatch batch : batches) {
            boolean uploadSuccessful = uploader.upload(batch).get();
            if (uploadSuccessful) {
              eventStorage.deleteBatch(batch.id());
            }
          }
          System.out.println("Finished upload");
        } catch (InterruptedException | ExecutionException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  @Override
  public void shutdown() {
    eventStorage.batch();
    uploadQueue.add("UPLOAD");
    try {
      writeThread.awaitTermination(0, TimeUnit.SECONDS);
      uploadThread.awaitTermination(6, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    isStopped = true;
  }

  @Override
  public void send(String name, ConfidenceValue.Struct context) {
    send(name, ConfidenceValue.of(ImmutableMap.of()), context);
  }

  @Override
  public void send(String name, ConfidenceValue.Struct message, ConfidenceValue.Struct context) {
    writeQueue.add(new Event(name, message, context));
  }
}
