package org.springframework.scheduling;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import org.jspecify.annotations.Nullable;

public interface TriggerContext {
   default Clock getClock() {
      return Clock.systemDefaultZone();
   }

   @Deprecated(since = "6.0")
   default @Nullable Date lastScheduledExecutionTime() {
      Instant instant = this.lastScheduledExecution();
      return instant != null ? Date.from(instant) : null;
   }

   @Nullable Instant lastScheduledExecution();

   @Deprecated(since = "6.0")
   default @Nullable Date lastActualExecutionTime() {
      Instant instant = this.lastActualExecution();
      return instant != null ? Date.from(instant) : null;
   }

   @Nullable Instant lastActualExecution();

   @Deprecated(since = "6.0")
   default @Nullable Date lastCompletionTime() {
      Instant instant = this.lastCompletion();
      return instant != null ? Date.from(instant) : null;
   }

   @Nullable Instant lastCompletion();
}
