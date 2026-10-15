package org.springframework.format.datetime.standard;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.core.NamedThreadLocal;

public final class DateTimeContextHolder {
   private static final ThreadLocal<DateTimeContext> dateTimeContextHolder = new NamedThreadLocal("DateTimeContext");

   private DateTimeContextHolder() {
   }

   public static void resetDateTimeContext() {
      dateTimeContextHolder.remove();
   }

   public static void setDateTimeContext(@Nullable DateTimeContext dateTimeContext) {
      if (dateTimeContext == null) {
         resetDateTimeContext();
      } else {
         dateTimeContextHolder.set(dateTimeContext);
      }
   }

   public static @Nullable DateTimeContext getDateTimeContext() {
      return dateTimeContextHolder.get();
   }

   public static DateTimeFormatter getFormatter(DateTimeFormatter formatter, @Nullable Locale locale) {
      DateTimeFormatter formatterToUse = locale != null ? formatter.withLocale(locale) : formatter;
      DateTimeContext context = getDateTimeContext();
      return context != null ? context.getFormatter(formatterToUse) : formatterToUse;
   }
}
