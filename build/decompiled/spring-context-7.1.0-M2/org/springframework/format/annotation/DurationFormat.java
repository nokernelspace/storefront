package org.springframework.format.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
public @interface DurationFormat {
   DurationFormat.Style style() default DurationFormat.Style.ISO8601;

   DurationFormat.Unit defaultUnit() default DurationFormat.Unit.MILLIS;

   enum Style {
      ISO8601,
      SIMPLE,
      COMPOSITE;
   }

   enum Unit {
      NANOS(ChronoUnit.NANOS, "ns", Duration::toNanos),
      MICROS(ChronoUnit.MICROS, "us", duration -> duration.toNanos() / 1000L),
      MILLIS(ChronoUnit.MILLIS, "ms", Duration::toMillis),
      SECONDS(ChronoUnit.SECONDS, "s", Duration::toSeconds),
      MINUTES(ChronoUnit.MINUTES, "m", Duration::toMinutes),
      HOURS(ChronoUnit.HOURS, "h", Duration::toHours),
      DAYS(ChronoUnit.DAYS, "d", Duration::toDays);

      private final ChronoUnit chronoUnit;
      private final String suffix;
      private final Function<Duration, Long> longValue;

      Unit(ChronoUnit chronoUnit, String suffix, Function<Duration, Long> toUnit) {
         this.chronoUnit = chronoUnit;
         this.suffix = suffix;
         this.longValue = toUnit;
      }

      public ChronoUnit asChronoUnit() {
         return this.chronoUnit;
      }

      public String asSuffix() {
         return this.suffix;
      }

      public Duration parse(String value) {
         return Duration.of(Long.parseLong(value), this.asChronoUnit());
      }

      public String print(Duration value) {
         return this.longValue(value) + this.asSuffix();
      }

      public long longValue(Duration value) {
         return this.longValue.apply(value);
      }

      public static DurationFormat.Unit fromChronoUnit(@Nullable ChronoUnit chronoUnit) {
         if (chronoUnit == null) {
            return MILLIS;
         }

         for (DurationFormat.Unit candidate : values()) {
            if (candidate.chronoUnit == chronoUnit) {
               return candidate;
            }
         }

         throw new IllegalArgumentException("No matching Unit for ChronoUnit." + chronoUnit.name());
      }

      public static DurationFormat.Unit fromSuffix(String suffix) {
         for (DurationFormat.Unit candidate : values()) {
            if (candidate.suffix.equalsIgnoreCase(suffix)) {
               return candidate;
            }
         }

         throw new IllegalArgumentException("'" + suffix + "' is not a valid simple duration Unit");
      }
   }
}
