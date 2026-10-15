package org.springframework.context.i18n;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

public class SimpleLocaleContext implements LocaleContext {
   private final @Nullable Locale locale;

   public SimpleLocaleContext(@Nullable Locale locale) {
      this.locale = locale;
   }

   @Override
   public @Nullable Locale getLocale() {
      return this.locale;
   }

   @Override
   public String toString() {
      return this.locale != null ? this.locale.toString() : "-";
   }
}
