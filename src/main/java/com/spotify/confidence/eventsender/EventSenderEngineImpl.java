package com.spotify.confidence.eventsender;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

class EventSenderEngineImpl implements EventSenderEngine {
  private final ExecutorService writeThread = Executors.newSingleThreadExecutor();
  private final ExecutorService uploadThread = Executors.newSingleThreadExecutor();
  private final BlockingQueue<Optional<Event>> writeQueue = new LinkedBlockingQueue<>();
  private final BlockingQueue<String> uploadQueue = new LinkedBlockingQueue<>();
  private final BlockingQueue<String> shutdownQueue = new LinkedBlockingQueue<>(1);
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
          Optional<Event> writeMessage = writeQueue.take();
          if(writeMessage.isPresent()) {
            Event event = writeMessage.get();
            eventStorage.write(event);
            flushPolicies.forEach(FlushPolicy::hit);

            if (flushPolicies.stream().anyMatch(FlushPolicy::shouldFlush)) {
              flushPolicies.forEach(FlushPolicy::reset);
              eventStorage.createBatch();
              uploadQueue.add(UPLOAD_SIG);
            }
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
          System.out.println(
              "New upload loop "
                  + batches.stream()
                      .flatMap(e -> e.events().stream().map(v -> v.name()))
                      .collect(Collectors.toList()));
          for (EventBatch batch : batches) {
            List<Event> toBeRetried = eventUploader.upload(batch).get();
            if (!toBeRetried.isEmpty()) {
              eventStorage.deleteBatch(batch.id(), toBeRetried);
            } else {
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
  public void send(String name, Value.Struct context) {
    send(name, Value.of(ImmutableMap.of()), context);
  }

  @Override
  public void send(String name, Value.Struct message, Value.Struct context) {
    if (!isStopped) {
      writeQueue.add(Optional.of(new Event(name, message, context)));
    }
  }

  @Override
  public void close() {
    System.out.println("Closing...");
    // stop accepting new events
    final ExecutorService thread = Executors.newSingleThreadExecutor();
    thread.submit(
        () -> {
          try {
            // wait until all the events in the queue are written
            sendFlushEvent();
            shutdownQueue.take();
            // create the final batch
            eventStorage.createBatch();
            uploadQueue.add(SHUTDOWN_UPLOAD);
            // wait until all the written events are uploaded
            shutdownQueue.take();
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

  private void sendFlushEvent() {
    writeQueue.add(Optional.absent());
  }
}
