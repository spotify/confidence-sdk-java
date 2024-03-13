package com.spotify.confidence.eventsender;

import static com.spotify.confidence.eventsender.EventSenderTestUtils.getFlushPolicies;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class EventSenderEngineImplTest {

  @Test
  public void testEngineUploads() throws IOException {
    final IntermittentErrorUploader alwaysSucceedUploader =
        new IntermittentErrorUploader(List.of());
    final int batchSize = 6;
    final int numEvents = 14;
    final EventSenderEngine engine =
        new EventSenderEngineImpl(getFlushPolicies(10000, batchSize), alwaysSucceedUploader);
    int size = 0;
    while (size++ < numEvents) {
      engine.send("eventDefinitions/navigate", Value.of(ImmutableMap.of("key", Value.of("size"))));
    }

    engine.close(); // Should trigger the upload of an additional incomplete batch
    final int additionalBatch = (numEvents % batchSize) > 0 ? 1 : 0;

    assertThat(alwaysSucceedUploader.uploadCalls.size())
        .isEqualTo((numEvents / batchSize + additionalBatch));
  }

  @Test
  public void testEngineCloseSuccessfullyWithoutEventsQueued() throws IOException {
    final IntermittentErrorUploader alwaysSucceedUploader =
        new IntermittentErrorUploader(List.of());
    final int batchSize = 6;
    final EventSenderEngine engine =
        new EventSenderEngineImpl(getFlushPolicies(10000, batchSize), alwaysSucceedUploader);

    engine.close(); // Should trigger the upload of an additional incomplete batch
  }

  @Test
  public void testEngineUploadsWhenIntermittentErrorWillRetry()
      throws IOException, InterruptedException {
    int batchSize = 3;
    int numEvents = 14;
    // This will fail at the 2nd and 5th upload
    final List<Integer> failAtUploadWithIndex = List.of(2, 5);
    final IntermittentErrorUploader fakeUploader =
        new IntermittentErrorUploader(failAtUploadWithIndex);
    final EventSenderEngine engine =
        new EventSenderEngineImpl(getFlushPolicies(10000, batchSize), fakeUploader);
    int size = 0;
    while (size++ < numEvents) {
      engine.send(
          "eventDefinitions/navigate", Value.of(ImmutableMap.of("key", Value.of("size=" + size))));
    }
    Thread.sleep(5000);

    engine.close(); // Should trigger the upload of an additional incomplete batch
    final int additionalBatch = (numEvents % batchSize) > 0 ? 1 : 0;

    // Verify we had the correct number of calls to the uploader (including retries)
    assertThat(fakeUploader.uploadCalls.size())
        .isEqualTo((numEvents / batchSize + additionalBatch) + failAtUploadWithIndex.size());
    // Verify we had the correct number of unique calls to the uploader
    assertThat(Set.copyOf(fakeUploader.uploadCalls).size())
        .isEqualTo((numEvents / batchSize + additionalBatch));
  }
}
