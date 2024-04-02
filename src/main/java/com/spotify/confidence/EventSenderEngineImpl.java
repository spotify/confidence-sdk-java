package com.spotify.confidence;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

class EventSenderEngineImpl implements EventSenderEngine {
  static final String EVENT_NAME_PREFIX = "eventDefinitions/";
  private final ExecutorService writeThread = Executors.newSingleThreadExecutor();
  private final ExecutorService uploadThread = Executors.newSingleThreadExecutor();
  private final ConcurrentLinkedQueue<Event> writeQueue = new ConcurrentLinkedQueue<>();
  private final BlockingQueue<String> uploadQueue = new LinkedBlockingQueue<>();
  private final BlockingQueue<String> shutdownQueue = new LinkedBlockingQueue<>(1);
  private final EventSenderStorage eventStorage = new InMemoryStorage();
  private final EventUploader eventUploader;
  private final int maxBatchSize;
  private final Clock clock;
  private static final String UPLOAD_SIG = "UPLOAD";
  private static final String SHUTDOWN_UPLOAD = "SHUTDOWN_UPLOAD";
  private static final String SHUTDOWN_UPLOAD_COMPLETED = "SHUTDOWN_UPLOAD_COMPLETED";
  private static final String SHUTDOWN_WRITE_COMPLETED = "SHUTDOWN_WRITE_COMPLETED";
  private volatile boolean isStopped = false;

  EventSenderEngineImpl(int maxBatchSize, EventUploader eventUploader, Clock clock) {
    this.maxBatchSize = maxBatchSize;
    this.eventUploader = eventUploader;
    this.clock = clock;
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
        final var numberOfPendingEvents = eventStorage.write(event);
        if (numberOfPendingEvents >= maxBatchSize) {
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
  public void send(
      String name, ConfidenceValue.Struct context, Optional<ConfidenceValue.Struct> message) {
    if (!isStopped) {
      writeQueue.add(
          new Event(
              EVENT_NAME_PREFIX + name,
              message.orElse(ConfidenceValue.Struct.EMPTY),
              context,
              clock.currentTimeSeconds()));
    }
  }

  @Override
  public void close() {
    // stop accepting new events
    isStopped = true;
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
          } finally {
            try {
              closeNow();
            } catch (IOException e) {
              // ignore
            }
          }
        });

    thread.shutdown();

    try {
      thread.awaitTermination(20, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private void closeNow() throws IOException {
    eventUploader.close();
    writeThread.shutdownNow();
    uploadThread.shutdownNow();
  }
}
