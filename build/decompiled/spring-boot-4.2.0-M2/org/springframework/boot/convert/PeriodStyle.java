package org.springframework.boot.convert;

import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public enum PeriodStyle {
   SIMPLE("^(?:([-+]?[0-9]+)Y)?(?:([-+]?[0-9]+)M)?(?:([-+]?[0-9]+)W)?(?:([-+]?[0-9]+)D)?$", 2) {
      @Override
      public Period parse(String value, @Nullable ChronoUnit unit) {
         try {
            if (PeriodStyle.NUMERIC.matcher(value).matches()) {
               return PeriodStyle.Unit.fromChronoUnit(unit).parse(value);
            }

            Matcher matcher = this.matcher(value);
            Assert.state(matcher.matches(), "Does not match simple period pattern");
            Assert.isTrue(this.hasAtLeastOneGroupValue(matcher), () -> "'" + value + "' is not a valid simple period");
            int years = this.parseInt(matcher, 1);
            int months = this.parseInt(matcher, 2);
            int weeks = this.parseInt(matcher, 3);
            int days = this.parseInt(matcher, 4);
            return Period.of(years, months, Math.addExact(Math.multiplyExact(weeks, 7), days));
         } catch (Exception ex) {
            throw new IllegalArgumentException("'" + value + "' is not a valid simple period", ex);
         }
      }

      boolean hasAtLeastOneGroupValue(Matcher matcher) {
         for (int i = 0; i < matcher.groupCount(); i++) {
            if (matcher.group(i + 1) != null) {
               return true;
            }
         }

         return false;
      }

      private int parseInt(Matcher matcher, int group) {
         String value = matcher.group(group);
         return value != null ? Integer.parseInt(value) : 0;
      }

      @Override
      protected boolean matches(String value) {
         return PeriodStyle.NUMERIC.matcher(value).matches() || this.matcher(value).matches();
      }

      @Override
      public String print(Period value, @Nullable ChronoUnit unit) {
         if (value.isZero()) {
            return PeriodStyle.Unit.fromChronoUnit(unit).print(value);
         }

         StringBuilder result = new StringBuilder();
         this.append(result, value, PeriodStyle.Unit.YEARS);
         this.append(result, value, PeriodStyle.Unit.MONTHS);
         this.append(result, value, PeriodStyle.Unit.DAYS);
         return result.toString();
      }

      private void append(StringBuilder result, Period value, PeriodStyle.Unit unit) {
         if (!unit.isZero(value)) {
            result.append(unit.print(value));
         }
      }
   },
   ISO8601("^[+-]?P.*$", 2) {
      @Override
      public Period parse(String value, @Nullable ChronoUnit unit) {
         try {
            return Period.parse(value);
         } catch (Exception ex) {
            throw new IllegalArgumentException("'" + value + "' is not a valid ISO-8601 period", ex);
         }
      }

      @Override
      public String print(Period value, @Nullable ChronoUnit unit) {
         return value.toString();
      }
   };

   private static final Pattern NUMERIC = Pattern.compile("^[-+]?[0-9]+$");
   private final Pattern pattern;

   PeriodStyle(String pattern, int flags) {
      this.pattern = Pattern.compile(pattern, flags);
   }

   protected boolean matches(String value) {
      return this.pattern.matcher(value).matches();
   }

   protected final Matcher matcher(String value) {
      return this.pattern.matcher(value);
   }

   public Period parse(String value) {
      return this.parse(value, null);
   }

   public abstract Period parse(String value, @Nullable ChronoUnit unit);

   public String print(Period value) {
      return this.print(value, null);
   }

   public abstract String print(Period value, @Nullable ChronoUnit unit);

   public static Period detectAndParse(String value) {
      return detectAndParse(value, null);
   }

   public static Period detectAndParse(String value, @Nullable ChronoUnit unit) {
      return detect(value).parse(value, unit);
   }

   public static PeriodStyle detect(String value) {
      Assert.notNull(value, "'value' must not be null");

      for (PeriodStyle candidate : values()) {
         if (candidate.matches(value)) {
            return candidate;
         }
      }

      throw new IllegalArgumentException("'" + value + "' is not a valid period");
   }

   private enum Unit {
      DAYS(ChronoUnit.DAYS, "d", Period::getDays, Period::ofDays),
      WEEKS(ChronoUnit.WEEKS, "w", null, Period::ofWeeks),
      MONTHS(ChronoUnit.MONTHS, "m", Period::getMonths, Period::ofMonths),
      YEARS(ChronoUnit.YEARS, "y", Period::getYears, Period::ofYears);

      private final ChronoUnit chronoUnit;
      private final String suffix;
      private final @Nullable Function<Period, Integer> intValue;
      private final Function<Integer, Period> factory;

      Unit(ChronoUnit chronoUnit, String suffix, @Nullable Function<Period, Integer> intValue, Function<Integer, Period> factory) {
         this.chronoUnit = chronoUnit;
         this.suffix = suffix;
         this.intValue = intValue;
         this.factory = factory;
      }

      private Period parse(String value) {
         return this.factory.apply(Integer.parseInt(value));
      }

      private String print(Period value) {
         return this.intValue(value) + this.suffix;
      }

      private boolean isZero(Period value) {
         return this.intValue(value) == 0;
      }

      private int intValue(Period value) {
         Assert.state(this.intValue != null, () -> "intValue cannot be extracted from " + this.name());
         return this.intValue.apply(value);
      }

      private static PeriodStyle.Unit fromChronoUnit(@Nullable ChronoUnit chronoUnit) {
         if (chronoUnit == null) {
            return DAYS;
         }

         for (PeriodStyle.Unit candidate : values()) {
            if (candidate.chronoUnit == chronoUnit) {
               return candidate;
            }
         }

         throw new IllegalArgumentException("Unsupported unit " + chronoUnit);
      }
   }
}
