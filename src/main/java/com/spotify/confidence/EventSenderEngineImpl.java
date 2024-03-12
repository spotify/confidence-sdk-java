package com.spotify.confidence;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

class EventSenderEngineImpl implements EventSenderEngine {
  private final BlockingQueue<Event> writeQueue = new LinkedBlockingQueue<>();
  private final BlockingQueue<String> uploadQueue = new LinkedBlockingQueue<>();
  private final EventSenderStorage eventStorage =
      new EventSenderStorage() {
        private final List<Event> events = new ArrayList<>();
        private final List<EventBatch> readyBatches = new ArrayList<>();
        final Semaphore semaphore = new Semaphore(1);

        @Override
        public void write(Event event) {
          runWithSemaphore(
              () -> {
                this.events.add(event);
              });
        }

        void runWithSemaphore(Runnable codeBlock) {
          try {
            semaphore.acquire(); // Acquire the semaphore, blocking if necessary
            // Execute the provided code block
            codeBlock.run();
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          } finally {
            semaphore.release(); // Release the semaphore after executing the code block
          }
        }

        @Override
        public void batch() {
          runWithSemaphore(
              () -> {
                EventBatch batch = new EventBatch(List.copyOf(events));
                events.clear();
                readyBatches.add(batch);
              });
        }

        @Override
        public List<EventBatch> readyEvents() {
          return readyBatches;
        }

        @Override
        public void deleteBatch(String batchId) {
          runWithSemaphore(
              () -> {
                readyBatches.removeIf(eventBatch -> eventBatch.id().equals(batchId));
                System.out.println("deleted");
              });
        }
      };
  private final List<FlushPolicy> flushPolicies;
  private boolean isStopped = false;

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

  ExecutorService writeThread = Executors.newSingleThreadExecutor();
  ExecutorService uploadThread = Executors.newSingleThreadExecutor();

  EventSenderEngineImpl(List<FlushPolicy> flushPolicyList) {
    this.flushPolicies = flushPolicyList;
    writeThread.submit(new WritePoller());
    uploadThread.submit(new UploadPoller());
  }

  @Override
  public void send(String name, ConfidenceValue.Struct message, ConfidenceValue.Struct context) {
    boolean offer = writeQueue.add(new Event(name, message, context));
  }

  @Override
  public void send(String name, ConfidenceValue.Struct context) {
    send(name, ConfidenceValue.of(ImmutableMap.of()), context);
  }
}

class Event {
  private final String name;
  private final ConfidenceValue.Struct message;
  private final ConfidenceValue.Struct context;

  public Event(String name, ConfidenceValue.Struct message, ConfidenceValue.Struct context) {
    this.name = name;
    this.message = message;
    this.context = context;
  }
}

class EventBatch {
  private final List<Event> events;
  private String id = UUID.randomUUID().toString();

  public EventBatch(List<Event> events) {
    this.events = events;
  }

  public String id() {
    return id;
  }

  public List<Event> events() {
    return events;
  }
}

interface EventSenderStorage {
  void write(Event event);

  void batch();

  List<EventBatch> readyEvents();

  void deleteBatch(String batchId);
}