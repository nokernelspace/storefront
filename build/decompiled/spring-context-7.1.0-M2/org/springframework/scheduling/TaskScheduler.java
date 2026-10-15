package org.springframework.scheduling;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;
import org.jspecify.annotations.Nullable;

public interface TaskScheduler {
   default Clock getClock() {
      return Clock.systemDefaultZone();
   }

   @Nullable ScheduledFuture<?> schedule(Runnable task, Trigger trigger);

   ScheduledFuture<?> schedule(Runnable task, Instant startTime);

   @Deprecated(since = "6.0")
   default ScheduledFuture<?> schedule(Runnable task, Date startTime) {
      return this.schedule(task, startTime.toInstant());
   }

   ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period);

   @Deprecated(since = "6.0")
   default ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Date startTime, long period) {
      return this.scheduleAtFixedRate(task, startTime.toInstant(), Duration.ofMillis(period));
   }

   ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period);

   @Deprecated(since = "6.0")
   default ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long period) {
      return this.scheduleAtFixedRate(task, Duration.ofMillis(period));
   }

   ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay);

   @Deprecated(since = "6.0")
   default ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Date startTime, long delay) {
      return this.scheduleWithFixedDelay(task, startTime.toInstant(), Duration.ofMillis(delay));
   }

   ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay);

   @Deprecated(since = "6.0")
   default ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long delay) {
      return this.scheduleWithFixedDelay(task, Duration.ofMillis(delay));
   }
}
