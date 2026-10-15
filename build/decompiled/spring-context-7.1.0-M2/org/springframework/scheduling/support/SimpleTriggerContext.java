package org.springframework.scheduling.support;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.TriggerContext;

public class SimpleTriggerContext implements TriggerContext {
   private final Clock clock;
   private volatile @Nullable Instant lastScheduledExecution;
   private volatile @Nullable Instant lastActualExecution;
   private volatile @Nullable Instant lastCompletion;

   public SimpleTriggerContext() {
      this.clock = Clock.systemDefaultZone();
   }

   @Deprecated(since = "6.0")
   public SimpleTriggerContext(@Nullable Date lastScheduledExecutionTime, @Nullable Date lastActualExecutionTime, @Nullable Date lastCompletionTime) {
      this(toInstant(lastScheduledExecutionTime), toInstant(lastActualExecutionTime), toInstant(lastCompletionTime));
   }

   private static @Nullable Instant toInstant(@Nullable Date date) {
      return date != null ? date.toInstant() : null;
   }

   public SimpleTriggerContext(@Nullable Instant lastScheduledExecution, @Nullable Instant lastActualExecution, @Nullable Instant lastCompletion) {
      this();
      this.lastScheduledExecution = lastScheduledExecution;
      this.lastActualExecution = lastActualExecution;
      this.lastCompletion = lastCompletion;
   }

   public SimpleTriggerContext(Clock clock) {
      this.clock = clock;
   }

   @Deprecated(since = "6.0")
   public void update(@Nullable Date lastScheduledExecutionTime, @Nullable Date lastActualExecutionTime, @Nullable Date lastCompletionTime) {
      this.update(toInstant(lastScheduledExecutionTime), toInstant(lastActualExecutionTime), toInstant(lastCompletionTime));
   }

   public void update(@Nullable Instant lastScheduledExecution, @Nullable Instant lastActualExecution, @Nullable Instant lastCompletion) {
      this.lastScheduledExecution = lastScheduledExecution;
      this.lastActualExecution = lastActualExecution;
      this.lastCompletion = lastCompletion;
   }

   @Override
   public Clock getClock() {
      return this.clock;
   }

   @Override
   public @Nullable Instant lastScheduledExecution() {
      return this.lastScheduledExecution;
   }

   @Override
   public @Nullable Instant lastActualExecution() {
      return this.lastActualExecution;
   }

   @Override
   public @Nullable Instant lastCompletion() {
      return this.lastCompletion;
   }
}
