package org.springframework.web.server.i18n;

import java.util.Locale;
import java.util.TimeZone;
import org.jspecify.annotations.Nullable;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.TimeZoneAwareLocaleContext;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

public class FixedLocaleContextResolver implements LocaleContextResolver {
   private final Locale locale;
   private final @Nullable TimeZone timeZone;

   public FixedLocaleContextResolver() {
      this(Locale.getDefault());
   }

   public FixedLocaleContextResolver(Locale locale) {
      this(locale, null);
   }

   public FixedLocaleContextResolver(Locale locale, @Nullable TimeZone timeZone) {
      Assert.notNull(locale, "Locale must not be null");
      this.locale = locale;
      this.timeZone = timeZone;
   }

   @Override
   public LocaleContext resolveLocaleContext(ServerWebExchange exchange) {
      return new TimeZoneAwareLocaleContext() {
         public Locale getLocale() {
            return FixedLocaleContextResolver.this.locale;
         }

         public @Nullable TimeZone getTimeZone() {
            return FixedLocaleContextResolver.this.timeZone;
         }
      };
   }

   @Override
   public void setLocaleContext(ServerWebExchange exchange, @Nullable LocaleContext localeContext) {
      throw new UnsupportedOperationException("Cannot change fixed locale - use a different locale context resolution strategy");
   }
}
