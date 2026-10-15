package org.springframework.scheduling;

import java.time.Instant;
import java.util.Date;
import org.jspecify.annotations.Nullable;

public interface Trigger {
   @Deprecated(since = "6.0")
   default @Nullable Date nextExecutionTime(TriggerContext triggerContext) {
      Instant instant = this.nextExecution(triggerContext);
      return instant != null ? Date.from(instant) : null;
   }

   @Nullable Instant nextExecution(TriggerContext triggerContext);
}
