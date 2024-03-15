package com.spotify.confidence.eventsender;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Scanner;

public class Demo {
  private static final FlushPolicy maxEventsFlushPolicy = new MaxEventsFlushPolicy(3);

  public static void main(String[] args) {
    final EventSenderEngineImpl eventSenderEngine =
        new EventSenderEngineImpl(
            List.of(maxEventsFlushPolicy),
            new GrpcEventUploader("xa0fQ4WKSvuxdjPtesupleiSbZeik6Gf"));

    while (true) {
      // take keyboard input with number 1-5
      final Integer input = takeInput();
      if (input == null) break;
      // create event with input as value & send event to eventSenderEngine
      final Value.Struct context = Value.of(ImmutableMap.of("environment", Value.of("demo-app")));
      eventSenderEngine.send(
          "eventDefinitions/input-received",
          Value.of(
              ImmutableMap.of(
                  "number", Value.of(input),
                  "string", Value.of("input " + input + " received"),
                  "struct", Value.of(ImmutableMap.of("key", Value.of("value"))))),
          context);
    }
    eventSenderEngine.close();
  }

  private static Integer takeInput() {
    System.out.println("Enter a number between 1-100");
    final Scanner scanner = new Scanner(System.in);
    final int input = scanner.nextInt();
    if (input < 1 || input > 100) {
      System.out.println("Exiting...");
      return null;
    }
    return input;
  }

  private static class MaxEventsFlushPolicy implements FlushPolicy {
    private final int maxNumberOfEventsInBatch;
    private int eventsInBatch = 0;

    public MaxEventsFlushPolicy(int maxNumberOfEventsInBatch) {
      this.maxNumberOfEventsInBatch = maxNumberOfEventsInBatch;
    }

    @Override
    public void hit() {
      System.out.println("MaxEventsFlushPolicy hit");
      eventsInBatch++;
    }

    @Override
    public boolean shouldFlush() {
      final boolean flush = eventsInBatch >= maxNumberOfEventsInBatch;
      System.out.println("MaxEventsFlushPolicy shouldFlush: " + flush);
      return flush;
    }

    @Override
    public void reset() {
      eventsInBatch = 0;
    }
  }
}
