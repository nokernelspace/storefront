package org.springframework.boot.context.metrics.buffering;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.springframework.core.metrics.StartupStep;

public class StartupTimeline {
   private final Instant startTime;
   private final List<StartupTimeline.TimelineEvent> events;

   StartupTimeline(Instant startTime, List<StartupTimeline.TimelineEvent> events) {
      this.startTime = startTime;
      this.events = Collections.unmodifiableList(events);
   }

   public Instant getStartTime() {
      return this.startTime;
   }

   public List<StartupTimeline.TimelineEvent> getEvents() {
      return this.events;
   }

   public static class TimelineEvent {
      private final BufferedStartupStep step;
      private final Instant endTime;
      private final Duration duration;

      TimelineEvent(BufferedStartupStep step, Instant endTime) {
         this.step = step;
         this.endTime = endTime;
         this.duration = Duration.between(step.getStartTime(), endTime);
      }

      public Instant getStartTime() {
         return this.step.getStartTime();
      }

      public Instant getEndTime() {
         return this.endTime;
      }

      public Duration getDuration() {
         return this.duration;
      }

      public StartupStep getStartupStep() {
         return this.step;
      }
   }
}
