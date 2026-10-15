package org.springframework.web.server.i18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.SimpleLocaleContext;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

public class AcceptHeaderLocaleContextResolver implements LocaleContextResolver {
   private final List<Locale> supportedLocales = new ArrayList<>(4);
   private @Nullable Locale defaultLocale;

   public void setSupportedLocales(List<Locale> locales) {
      this.supportedLocales.clear();
      this.supportedLocales.addAll(locales);
   }

   public List<Locale> getSupportedLocales() {
      return this.supportedLocales;
   }

   public void setDefaultLocale(@Nullable Locale defaultLocale) {
      this.defaultLocale = defaultLocale;
   }

   public @Nullable Locale getDefaultLocale() {
      return this.defaultLocale;
   }

   @Override
   public LocaleContext resolveLocaleContext(ServerWebExchange exchange) {
      List<Locale> requestLocales = null;

      try {
         requestLocales = exchange.getRequest().getHeaders().getAcceptLanguageAsLocales();
      } catch (IllegalArgumentException var4) {
      }

      return new SimpleLocaleContext(this.resolveSupportedLocale(requestLocales));
   }

   private @Nullable Locale resolveSupportedLocale(@Nullable List<Locale> requestLocales) {
      if (CollectionUtils.isEmpty(requestLocales)) {
         return this.getDefaultLocale();
      }

      List<Locale> supportedLocales = this.getSupportedLocales();
      if (supportedLocales.isEmpty()) {
         return requestLocales.get(0);
      }

      Locale languageMatch = null;

      for (Locale locale : requestLocales) {
         if (supportedLocales.contains(locale)) {
            if (languageMatch == null || languageMatch.getLanguage().equals(locale.getLanguage())) {
               return locale;
            }
         } else if (languageMatch == null) {
            for (Locale supportedLocale : supportedLocales) {
               if (!StringUtils.hasLength(supportedLocale.getCountry()) && supportedLocale.getLanguage().equals(locale.getLanguage())) {
                  languageMatch = supportedLocale;
                  break;
               }
            }
         }
      }

      if (languageMatch != null) {
         return languageMatch;
      }

      Locale defaultLocale = this.getDefaultLocale();
      return defaultLocale != null ? defaultLocale : requestLocales.get(0);
   }

   @Override
   public void setLocaleContext(ServerWebExchange exchange, @Nullable LocaleContext locale) {
      throw new UnsupportedOperationException("Cannot change HTTP accept header - use a different locale context resolution strategy");
   }
}
