package org.springframework.context.i18n;

import java.util.Locale;
import java.util.TimeZone;
import org.jspecify.annotations.Nullable;

public class SimpleTimeZoneAwareLocaleContext extends SimpleLocaleContext implements TimeZoneAwareLocaleContext {
   private final @Nullable TimeZone timeZone;

   public SimpleTimeZoneAwareLocaleContext(@Nullable Locale locale, @Nullable TimeZone timeZone) {
      super(locale);
      this.timeZone = timeZone;
   }

   @Override
   public @Nullable TimeZone getTimeZone() {
      return this.timeZone;
   }

   @Override
   public String toString() {
      return super.toString() + " " + (this.timeZone != null ? this.timeZone : "-");
   }
}
