package org.springframework.scheduling.support;

import java.time.DateTimeException;
import java.time.temporal.Temporal;
import java.time.temporal.ValueRange;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

final class BitsCronField extends CronField {
   public static final BitsCronField ZERO_NANOS = forZeroNanos();
   private static final long MASK = -1L;
   private long bits;

   private BitsCronField(CronField.Type type) {
      super(type);
   }

   private static BitsCronField forZeroNanos() {
      BitsCronField field = new BitsCronField(CronField.Type.NANO);
      field.setBit(0);
      return field;
   }

   public static BitsCronField parseSeconds(String value) {
      return parseField(value, CronField.Type.SECOND);
   }

   public static BitsCronField parseMinutes(String value) {
      return parseField(value, CronField.Type.MINUTE);
   }

   public static BitsCronField parseHours(String value) {
      return parseField(value, CronField.Type.HOUR);
   }

   public static BitsCronField parseDaysOfMonth(String value) {
      return parseDate(value, CronField.Type.DAY_OF_MONTH);
   }

   public static BitsCronField parseMonth(String value) {
      return parseField(value, CronField.Type.MONTH);
   }

   public static BitsCronField parseDaysOfWeek(String value) {
      BitsCronField result = parseDate(value, CronField.Type.DAY_OF_WEEK);
      if (result.getBit(0)) {
         result.setBit(7);
         result.clearBit(0);
      }

      return result;
   }

   private static BitsCronField parseDate(String value, CronField.Type type) {
      if (value.equals("?")) {
         value = "*";
      }

      return parseField(value, type);
   }

   private static BitsCronField parseField(String value, CronField.Type type) {
      Assert.hasLength(value, "Value must not be empty");
      Assert.notNull(type, "Type must not be null");

      try {
         BitsCronField result = new BitsCronField(type);
         String[] fields = StringUtils.delimitedListToStringArray(value, ",");

         for (String field : fields) {
            int slashPos = field.indexOf(47);
            if (slashPos == -1) {
               ValueRange range = parseRange(field, type);
               result.setBits(range);
            } else {
               String rangeStr = field.substring(0, slashPos);
               String deltaStr = field.substring(slashPos + 1);
               ValueRange range = parseRange(rangeStr, type);
               if (rangeStr.indexOf(45) == -1) {
                  range = ValueRange.of(range.getMinimum(), type.range().getMaximum());
               }

               int delta = Integer.parseInt(deltaStr);
               if (delta <= 0) {
                  throw new IllegalArgumentException("Incrementer delta must be 1 or higher");
               }

               result.setBits(range, delta);
            }
         }

         return result;
      } catch (DateTimeException | IllegalArgumentException ex) {
         String msg = ex.getMessage() + " '" + value + "'";
         throw new IllegalArgumentException(msg, ex);
      }
   }

   private static ValueRange parseRange(String value, CronField.Type type) {
      if (value.equals("*")) {
         return type.range();
      }

      int hyphenPos = value.indexOf(45);
      if (hyphenPos == -1) {
         int result = type.checkValidValue(Integer.parseInt(value));
         return ValueRange.of(result, result);
      }

      int min = Integer.parseInt(value, 0, hyphenPos, 10);
      int max = Integer.parseInt(value, hyphenPos + 1, value.length(), 10);
      min = type.checkValidValue(min);
      max = type.checkValidValue(max);
      if (type == CronField.Type.DAY_OF_WEEK && min == 7) {
         min = 0;
      }

      return ValueRange.of(min, max);
   }

   @Override
   public <T extends Temporal & Comparable<? super T>> @Nullable T nextOrSame(T temporal) {
      int current = this.type().get(temporal);
      int next = this.nextSetBit(current);
      if (next == -1) {
         temporal = this.type().rollForward(temporal);
         next = this.nextSetBit(this.type().get(temporal));
         if (next == -1) {
            temporal = this.type().rollForward(temporal);
            next = this.nextSetBit(0);
         }
      }

      if (next == current) {
         return temporal;
      }

      int count = 0;
      current = this.type().get(temporal);

      while (current != next && count++ < 366) {
         temporal = this.type().elapseUntil(temporal, next);
         current = this.type().get(temporal);
         next = this.nextSetBit(current);
         if (next == -1) {
            temporal = this.type().rollForward(temporal);
            next = this.nextSetBit(this.type().get(temporal));
            if (next == -1) {
               temporal = this.type().rollForward(temporal);
               next = this.nextSetBit(0);
            }

            current = this.type().get(temporal);
         }
      }

      return count >= 366 ? null : this.type().reset(temporal);
   }

   boolean getBit(int index) {
      return (this.bits & 1L << index) != 0L;
   }

   private int nextSetBit(int fromIndex) {
      long result = this.bits & -1L << fromIndex;
      return result != 0L ? Long.numberOfTrailingZeros(result) : -1;
   }

   private void setBits(ValueRange range) {
      if (range.getMinimum() == range.getMaximum()) {
         this.setBit((int)range.getMinimum());
      } else {
         long minMask = -1L << (int)range.getMinimum();
         long maxMask = -1L >>> (int)(-(range.getMaximum() + 1L));
         this.bits |= minMask & maxMask;
      }
   }

   private void setBits(ValueRange range, int delta) {
      if (delta == 1) {
         this.setBits(range);
      } else {
         for (int i = (int)range.getMinimum(); i <= range.getMaximum(); i += delta) {
            this.setBit(i);
         }
      }
   }

   private void setBit(int index) {
      this.bits |= 1L << index;
   }

   private void clearBit(int index) {
      this.bits &= ~(1L << index);
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof BitsCronField that && this.type() == that.type() && this.bits == that.bits;
   }

   @Override
   public int hashCode() {
      return Long.hashCode(this.bits);
   }

   @Override
   public String toString() {
      StringBuilder builder = new StringBuilder(this.type().toString());
      builder.append(" {");
      int i = this.nextSetBit(0);
      if (i != -1) {
         builder.append(i);

         for (int var3 = this.nextSetBit(i + 1); var3 != -1; var3 = this.nextSetBit(var3 + 1)) {
            builder.append(", ");
            builder.append(var3);
         }
      }

      builder.append('}');
      return builder.toString();
   }
}
