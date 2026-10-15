package org.springframework.core.metrics.jfr;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;

public class FlightRecorderApplicationStartup implements ApplicationStartup {
   private final AtomicLong currentSequenceId = new AtomicLong();
   private final Deque<Long> currentSteps = new ConcurrentLinkedDeque<>();

   public FlightRecorderApplicationStartup() {
      this.currentSteps.offerFirst(this.currentSequenceId.get());
   }

   @Override
   public StartupStep start(String name) {
      Long parentId = this.currentSteps.getFirst();
      long sequenceId = this.currentSequenceId.incrementAndGet();
      this.currentSteps.offerFirst(sequenceId);
      return new FlightRecorderStartupStep(sequenceId, name, parentId, committedStep -> this.currentSteps.removeFirstOccurrence(sequenceId));
   }
}
