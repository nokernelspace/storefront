package org.springframework.scheduling.support;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAdjusters;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

final class QuartzCronField extends CronField {
   private final CronField.Type rollForwardType;
   private final TemporalAdjuster adjuster;
   private final String value;

   private QuartzCronField(CronField.Type type, TemporalAdjuster adjuster, String value) {
      this(type, type, adjuster, value);
   }

   private QuartzCronField(CronField.Type type, CronField.Type rollForwardType, TemporalAdjuster adjuster, String value) {
      super(type);
      this.adjuster = adjuster;
      this.value = value;
      this.rollForwardType = rollForwardType;
   }

   public static boolean isQuartzDaysOfMonthField(String value) {
      return value.contains("L") || value.contains("W");
   }

   public static QuartzCronField parseDaysOfMonth(String value) {
      int idx = value.lastIndexOf(76);
      if (idx != -1) {
         if (idx != 0) {
            throw new IllegalArgumentException("Unrecognized characters before 'L' in '" + value + "'");
         }

         TemporalAdjuster adjuster;
         if (value.length() == 2 && value.charAt(1) == 'W') {
            adjuster = lastWeekdayOfMonth();
         } else if (value.length() == 1) {
            adjuster = lastDayOfMonth();
         } else {
            int offset = Integer.parseInt(value, idx + 1, value.length(), 10);
            if (offset >= 0) {
               throw new IllegalArgumentException("Offset '" + offset + " should be < 0 '" + value + "'");
            }

            adjuster = lastDayWithOffset(offset);
         }

         return new QuartzCronField(CronField.Type.DAY_OF_MONTH, adjuster, value);
      } else {
         idx = value.lastIndexOf(87);
         if (idx != -1) {
            if (idx == 0) {
               throw new IllegalArgumentException("No day-of-month before 'W' in '" + value + "'");
            }

            if (idx != value.length() - 1) {
               throw new IllegalArgumentException("Unrecognized characters after 'W' in '" + value + "'");
            }

            int dayOfMonth = Integer.parseInt(value, 0, idx, 10);
            dayOfMonth = CronField.Type.DAY_OF_MONTH.checkValidValue(dayOfMonth);
            TemporalAdjuster adjuster = weekdayNearestTo(dayOfMonth);
            return new QuartzCronField(CronField.Type.DAY_OF_MONTH, adjuster, value);
         } else {
            throw new IllegalArgumentException("No 'L' or 'W' found in '" + value + "'");
         }
      }
   }

   public static boolean isQuartzDaysOfWeekField(String value) {
      return value.contains("L") || value.contains("#");
   }

   public static QuartzCronField parseDaysOfWeek(String value) {
      int idx = value.lastIndexOf(76);
      if (idx != -1) {
         if (idx != value.length() - 1) {
            throw new IllegalArgumentException("Unrecognized characters after 'L' in '" + value + "'");
         }

         if (idx == 0) {
            throw new IllegalArgumentException("No day-of-week before 'L' in '" + value + "'");
         }

         DayOfWeek dayOfWeek = parseDayOfWeek(value.substring(0, idx));
         TemporalAdjuster adjuster = lastInMonth(dayOfWeek);
         return new QuartzCronField(CronField.Type.DAY_OF_WEEK, CronField.Type.DAY_OF_MONTH, adjuster, value);
      } else {
         idx = value.lastIndexOf(35);
         if (idx != -1) {
            if (idx == 0) {
               throw new IllegalArgumentException("No day-of-week before '#' in '" + value + "'");
            }

            if (idx == value.length() - 1) {
               throw new IllegalArgumentException("No ordinal after '#' in '" + value + "'");
            }

            DayOfWeek dayOfWeek = parseDayOfWeek(value.substring(0, idx));
            int ordinal = Integer.parseInt(value, idx + 1, value.length(), 10);
            if (ordinal <= 0) {
               throw new IllegalArgumentException("Ordinal '" + ordinal + "' in '" + value + "' must be positive number ");
            }

            TemporalAdjuster adjuster = dayOfWeekInMonth(ordinal, dayOfWeek);
            return new QuartzCronField(CronField.Type.DAY_OF_WEEK, CronField.Type.DAY_OF_MONTH, adjuster, value);
         } else {
            throw new IllegalArgumentException("No 'L' or '#' found in '" + value + "'");
         }
      }
   }

   private static DayOfWeek parseDayOfWeek(String value) {
      int dayOfWeek = Integer.parseInt(value);
      if (dayOfWeek == 0) {
         dayOfWeek = 7;
      }

      try {
         return DayOfWeek.of(dayOfWeek);
      } catch (DateTimeException ex) {
         throw new IllegalArgumentException(ex.getMessage() + " '" + value + "'", ex);
      }
   }

   private static TemporalAdjuster atMidnight() {
      return temporal -> temporal.isSupported(ChronoField.NANO_OF_DAY) ? temporal.with(ChronoField.NANO_OF_DAY, 0L) : temporal;
   }

   private static TemporalAdjuster lastDayOfMonth() {
      TemporalAdjuster adjuster = TemporalAdjusters.lastDayOfMonth();
      return temporal -> {
         Temporal result = adjuster.adjustInto(temporal);
         return rollbackToMidnight(temporal, result);
      };
   }

   private static TemporalAdjuster lastWeekdayOfMonth() {
      TemporalAdjuster adjuster = TemporalAdjusters.lastDayOfMonth();
      return temporal -> {
         Temporal lastDom = adjuster.adjustInto(temporal);
         int dow = lastDom.get(ChronoField.DAY_OF_WEEK);
         Temporal result;
         if (dow == 6) {
            result = lastDom.minus(1L, ChronoUnit.DAYS);
         } else if (dow == 7) {
            result = lastDom.minus(2L, ChronoUnit.DAYS);
         } else {
            result = lastDom;
         }

         return rollbackToMidnight(temporal, result);
      };
   }

   private static TemporalAdjuster lastDayWithOffset(int offset) {
      Assert.isTrue(offset < 0, "Offset should be < 0");
      TemporalAdjuster adjuster = TemporalAdjusters.lastDayOfMonth();
      return temporal -> {
         Temporal result = adjuster.adjustInto(temporal).plus(offset, ChronoUnit.DAYS);
         return rollbackToMidnight(temporal, result);
      };
   }

   private static TemporalAdjuster weekdayNearestTo(int dayOfMonth) {
      return temporal -> {
         int current = CronField.Type.DAY_OF_MONTH.get(temporal);
         DayOfWeek dayOfWeek = DayOfWeek.from(temporal);
         if ((current != dayOfMonth || !isWeekday(dayOfWeek))
            && (dayOfWeek != DayOfWeek.FRIDAY || current != dayOfMonth - 1)
            && (dayOfWeek != DayOfWeek.MONDAY || current != dayOfMonth + 1)
            && (dayOfWeek != DayOfWeek.MONDAY || dayOfMonth != 1 || current != 3)) {
            for (int count = 0; count++ < 366; current = CronField.Type.DAY_OF_MONTH.get(temporal)) {
               if (current == dayOfMonth) {
                  dayOfWeek = DayOfWeek.from(temporal);
                  if (dayOfWeek == DayOfWeek.SATURDAY) {
                     if (dayOfMonth != 1) {
                        temporal = temporal.minus(1L, ChronoUnit.DAYS);
                     } else {
                        temporal = temporal.plus(2L, ChronoUnit.DAYS);
                     }
                  } else if (dayOfWeek == DayOfWeek.SUNDAY) {
                     temporal = temporal.plus(1L, ChronoUnit.DAYS);
                  }

                  return atMidnight().adjustInto(temporal);
               }

               temporal = CronField.Type.DAY_OF_MONTH.elapseUntil(cast(temporal), dayOfMonth);
            }

            return null;
         } else {
            return temporal;
         }
      };
   }

   private static boolean isWeekday(DayOfWeek dayOfWeek) {
      return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
   }

   private static TemporalAdjuster lastInMonth(DayOfWeek dayOfWeek) {
      TemporalAdjuster adjuster = TemporalAdjusters.lastInMonth(dayOfWeek);
      return temporal -> {
         Temporal result = adjuster.adjustInto(temporal);
         return rollbackToMidnight(temporal, result);
      };
   }

   private static TemporalAdjuster dayOfWeekInMonth(int ordinal, DayOfWeek dayOfWeek) {
      TemporalAdjuster adjuster = TemporalAdjusters.dayOfWeekInMonth(ordinal, dayOfWeek);
      return temporal -> {
         for (int i = 0; i < 12; i++) {
            Temporal result = adjuster.adjustInto(temporal);
            if (result.get(ChronoField.MONTH_OF_YEAR) == temporal.get(ChronoField.MONTH_OF_YEAR)) {
               return rollbackToMidnight(temporal, result);
            }

            temporal = result;
         }

         return null;
      };
   }

   private static Temporal rollbackToMidnight(Temporal current, Temporal result) {
      return result.get(ChronoField.DAY_OF_MONTH) == current.get(ChronoField.DAY_OF_MONTH) ? current : atMidnight().adjustInto(result);
   }

   @Override
   public <T extends Temporal & Comparable<? super T>> @Nullable T nextOrSame(T temporal) {
      T result = this.adjust(temporal);
      if (result != null && result.compareTo(temporal) < 0) {
         temporal = this.rollForwardType.rollForward(temporal);
         result = this.adjust(temporal);
         if (result != null) {
            result = this.type().reset(result);
         }
      }

      return result;
   }

   private <T extends Temporal & Comparable<? super T>> @Nullable T adjust(T temporal) {
      return (T)this.adjuster.adjustInto(temporal);
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof QuartzCronField that && this.type() == that.type() && this.value.equals(that.value);
   }

   @Override
   public int hashCode() {
      return this.value.hashCode();
   }

   @Override
   public String toString() {
      return this.type() + " '" + this.value + "'";
   }
}
