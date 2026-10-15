package org.springframework.scheduling.support;

import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public final class CronExpression {
   static final int MAX_ATTEMPTS = 366;
   private static final String[] MACROS = new String[]{
      "@yearly",
      "0 0 0 1 1 *",
      "@annually",
      "0 0 0 1 1 *",
      "@monthly",
      "0 0 0 1 * *",
      "@weekly",
      "0 0 0 * * 0",
      "@daily",
      "0 0 0 * * *",
      "@midnight",
      "0 0 0 * * *",
      "@hourly",
      "0 0 * * * *"
   };
   private final CronField[] fields;
   private final String expression;

   private CronExpression(
      CronField seconds, CronField minutes, CronField hours, CronField daysOfMonth, CronField months, CronField daysOfWeek, String expression
   ) {
      this.fields = new CronField[]{daysOfWeek, months, daysOfMonth, hours, minutes, seconds, CronField.zeroNanos()};
      this.expression = expression;
   }

   public static CronExpression parse(String expression) {
      Assert.hasLength(expression, "Expression must not be empty");
      expression = resolveMacros(expression);
      String[] fields = StringUtils.tokenizeToStringArray(expression, " ");
      if (fields.length != 6) {
         throw new IllegalArgumentException(String.format("Cron expression must consist of 6 fields (found %d in \"%s\")", fields.length, expression));
      }

      try {
         CronField seconds = CronField.parseSeconds(fields[0]);
         CronField minutes = CronField.parseMinutes(fields[1]);
         CronField hours = CronField.parseHours(fields[2]);
         CronField daysOfMonth = CronField.parseDaysOfMonth(fields[3]);
         CronField months = CronField.parseMonth(fields[4]);
         CronField daysOfWeek = CronField.parseDaysOfWeek(fields[5]);
         return new CronExpression(seconds, minutes, hours, daysOfMonth, months, daysOfWeek, expression);
      } catch (IllegalArgumentException ex) {
         String msg = ex.getMessage() + " in cron expression \"" + expression + "\"";
         throw new IllegalArgumentException(msg, ex);
      }
   }

   public static boolean isValidExpression(@Nullable String expression) {
      if (expression == null) {
         return false;
      }

      try {
         parse(expression);
         return true;
      } catch (IllegalArgumentException ex) {
         return false;
      }
   }

   private static String resolveMacros(String expression) {
      expression = expression.trim();

      for (int i = 0; i < MACROS.length; i += 2) {
         if (MACROS[i].equalsIgnoreCase(expression)) {
            return MACROS[i + 1];
         }
      }

      return expression;
   }

   public <T extends Temporal & Comparable<? super T>> @Nullable T next(T temporal) {
      return this.nextOrSame(ChronoUnit.NANOS.addTo(temporal, 1L));
   }

   private <T extends Temporal & Comparable<? super T>> @Nullable T nextOrSame(T temporal) {
      for (int i = 0; i < 366; i++) {
         T result = this.nextOrSameInternal(temporal);
         if (result == null || result.equals(temporal)) {
            return result;
         }

         temporal = result;
      }

      return null;
   }

   private <T extends Temporal & Comparable<? super T>> @Nullable T nextOrSameInternal(T temporal) {
      for (CronField field : this.fields) {
         temporal = field.nextOrSame(temporal);
         if (temporal == null) {
            return null;
         }
      }

      return temporal;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof CronExpression that && Arrays.equals(this.fields, that.fields);
   }

   @Override
   public int hashCode() {
      return Arrays.hashCode(this.fields);
   }

   @Override
   public String toString() {
      return this.expression;
   }
}
