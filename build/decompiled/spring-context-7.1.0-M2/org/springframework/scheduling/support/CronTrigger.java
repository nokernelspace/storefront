package org.springframework.scheduling.support;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.TimeZone;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.util.Assert;

public class CronTrigger implements Trigger {
   private final CronExpression expression;
   private final @Nullable ZoneId zoneId;

   public CronTrigger(String expression) {
      this.expression = CronExpression.parse(expression);
      this.zoneId = null;
   }

   public CronTrigger(String expression, TimeZone timeZone) {
      this.expression = CronExpression.parse(expression);
      Assert.notNull(timeZone, "TimeZone must not be null");
      this.zoneId = timeZone.toZoneId();
   }

   public CronTrigger(String expression, ZoneId zoneId) {
      this.expression = CronExpression.parse(expression);
      Assert.notNull(zoneId, "ZoneId must not be null");
      this.zoneId = zoneId;
   }

   public String getExpression() {
      return this.expression.toString();
   }

   @Override
   public @Nullable Instant nextExecution(TriggerContext triggerContext) {
      Instant timestamp = this.determineLatestTimestamp(triggerContext);
      ZoneId zone = this.zoneId != null ? this.zoneId : triggerContext.getClock().getZone();
      ZonedDateTime zonedTimestamp = timestamp.atZone(zone);
      ZonedDateTime nextTimestamp = this.expression.next(zonedTimestamp);
      return nextTimestamp != null ? nextTimestamp.toInstant() : null;
   }

   Instant determineLatestTimestamp(TriggerContext triggerContext) {
      Instant timestamp = triggerContext.lastCompletion();
      if (timestamp != null) {
         Instant scheduled = triggerContext.lastScheduledExecution();
         if (scheduled != null && timestamp.isBefore(scheduled)) {
            timestamp = scheduled;
         }
      } else {
         timestamp = this.determineInitialTimestamp(triggerContext);
      }

      return timestamp;
   }

   Instant determineInitialTimestamp(TriggerContext triggerContext) {
      return triggerContext.getClock().instant();
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof CronTrigger that && this.expression.equals(that.expression) && Objects.equals(this.zoneId, that.zoneId);
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.expression, this.zoneId);
   }

   @Override
   public String toString() {
      return this.expression.toString();
   }

   public static CronTrigger forLenientExecution(String expression) {
      return new CronTrigger(expression);
   }

   public static CronTrigger resumeLenientExecution(String expression, Instant resumptionTimestamp) {
      return new CronTrigger(expression) {
         @Override
         Instant determineInitialTimestamp(TriggerContext triggerContext) {
            return resumptionTimestamp;
         }
      };
   }

   public static CronTrigger forFixedExecution(String expression) {
      return new CronTrigger(expression) {
         @Override
         protected Instant determineLatestTimestamp(TriggerContext triggerContext) {
            Instant scheduled = triggerContext.lastScheduledExecution();
            return scheduled != null ? scheduled : super.determineInitialTimestamp(triggerContext);
         }
      };
   }

   public static CronTrigger resumeFixedExecution(String expression, Instant resumptionTimestamp) {
      return new CronTrigger(expression) {
         @Override
         protected Instant determineLatestTimestamp(TriggerContext triggerContext) {
            Instant scheduled = triggerContext.lastScheduledExecution();
            return scheduled != null ? scheduled : super.determineLatestTimestamp(triggerContext);
         }

         @Override
         Instant determineInitialTimestamp(TriggerContext triggerContext) {
            return resumptionTimestamp;
         }
      };
   }
}
