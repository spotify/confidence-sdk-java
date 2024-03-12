package com.spotify.confidence.eventsender;

import static com.spotify.confidence.eventsender.EventSenderTestUtils.getFlushPolicies;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EventSenderEngineImplTest {
  @Mock EventUploader eventUploader;

  @BeforeEach
  public void setup() {
    when(eventUploader.upload(any()))
        .thenReturn(CompletableFuture.completedFuture(true));
  }

  @Test
  public void testEngineUploads() {
    int batchSize = 6;
    int numEvents = 14;
    try (EventSenderEngine engine = new EventSenderEngineImpl(getFlushPolicies(10000, batchSize), eventUploader)) {
      int size = 0;
      while (size++ < numEvents) {
        engine.send("event-" + size, ConfidenceValue.of(ImmutableMap.of()));
      }
      Thread.sleep(1000); // Wait for "close" and the correct uploading of events

      engine.close(); // Should trigger the upload of an additional incomplete batch
      int additionalBatch = (numEvents % batchSize) > 0 ? 1 : 0;

      verify(eventUploader, times(numEvents/batchSize + additionalBatch))
          .upload(any());
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
