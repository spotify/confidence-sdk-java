package com.spotify.confidence.eventsender;

import static com.spotify.confidence.eventsender.EventSenderTestUtils.getFlushPolicies;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EventSenderEngineImplTest {

  @Test
  public void testEngineUploads() throws InterruptedException {
    IntermittentErrorUploader alwaysSucceedUploader = new IntermittentErrorUploader(List.of());
    int batchSize = 6;
    int numEvents = 14;
    try (EventSenderEngine engine =
        new EventSenderEngineImpl(getFlushPolicies(10000, batchSize), alwaysSucceedUploader)) {
      int size = 0;
      while (size++ < numEvents) {
        engine.send(
            "eventDefinitions/navigate" + size,
            ConfidenceValue.of(ImmutableMap.of("key", ConfidenceValue.of("size"))));
      }
      engine.close(); // Early close to make sure all events are processed and flushed before
      // assertions
      int additionalBatch = (numEvents % batchSize) > 0 ? 1 : 0;
      assertThat(alwaysSucceedUploader.uploadCalls.size())
          .isEqualTo((numEvents / batchSize + additionalBatch));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testEngineUploadsWhenIntermittentErrorWillRetry() {
    int batchSize = 3;
    int numEvents = 14;
    // This will fail at the 2nd and 5th upload
    List<Integer> failAtUploadWithIndex = List.of(2, 5);
    IntermittentErrorUploader fakeUploader = new IntermittentErrorUploader(failAtUploadWithIndex);
    try (EventSenderEngine engine =
        new EventSenderEngineImpl(getFlushPolicies(10000, batchSize), fakeUploader)) {
      int size = 0;
      while (size++ < numEvents) {
        engine.send(
            "eventDefinitions/navigate",
            ConfidenceValue.of(ImmutableMap.of("key", ConfidenceValue.of("size=" + size))));
      }
      engine.close(); // Should trigger the upload of an additional incomplete batch
      int additionalBatch = (numEvents % batchSize) > 0 ? 1 : 0;

      // Verify we had the correct number of calls to the uploader (including retries)
      assertThat(fakeUploader.uploadCalls.size())
          .isEqualTo((numEvents / batchSize + additionalBatch) + failAtUploadWithIndex.size());
      // Verify we had the correct number of unique calls to the uploader
      assertThat(Set.copyOf(fakeUploader.uploadCalls).size())
          .isEqualTo((numEvents / batchSize + additionalBatch));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
