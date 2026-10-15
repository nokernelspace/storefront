package org.springframework.context.i18n;

import java.util.Locale;
import java.util.TimeZone;
import org.jspecify.annotations.Nullable;
import org.springframework.core.NamedInheritableThreadLocal;
import org.springframework.core.NamedThreadLocal;

public final class LocaleContextHolder {
   private static final ThreadLocal<LocaleContext> localeContextHolder = new NamedThreadLocal("LocaleContext");
   private static final ThreadLocal<LocaleContext> inheritableLocaleContextHolder = new NamedInheritableThreadLocal("LocaleContext");
   private static @Nullable Locale defaultLocale;
   private static @Nullable TimeZone defaultTimeZone;

   private LocaleContextHolder() {
   }

   public static void resetLocaleContext() {
      localeContextHolder.remove();
      inheritableLocaleContextHolder.remove();
   }

   public static void setLocaleContext(@Nullable LocaleContext localeContext) {
      setLocaleContext(localeContext, false);
   }

   public static void setLocaleContext(@Nullable LocaleContext localeContext, boolean inheritable) {
      if (localeContext == null) {
         resetLocaleContext();
      } else if (inheritable) {
         inheritableLocaleContextHolder.set(localeContext);
         localeContextHolder.remove();
      } else {
         localeContextHolder.set(localeContext);
         inheritableLocaleContextHolder.remove();
      }
   }

   public static @Nullable LocaleContext getLocaleContext() {
      LocaleContext localeContext = localeContextHolder.get();
      if (localeContext == null) {
         localeContext = inheritableLocaleContextHolder.get();
      }

      return localeContext;
   }

   public static void setLocale(@Nullable Locale locale) {
      setLocale(locale, false);
   }

   public static void setLocale(@Nullable Locale locale, boolean inheritable) {
      TimeZone timeZone = getLocaleContext() instanceof TimeZoneAwareLocaleContext timeZoneAware ? timeZoneAware.getTimeZone() : null;
      SimpleLocaleContext var5;
      if (timeZone != null) {
         var5 = new SimpleTimeZoneAwareLocaleContext(locale, timeZone);
      } else if (locale != null) {
         var5 = new SimpleLocaleContext(locale);
      } else {
         var5 = null;
      }

      setLocaleContext(var5, inheritable);
   }

   public static void setDefaultLocale(@Nullable Locale locale) {
      defaultLocale = locale;
   }

   public static Locale getLocale() {
      return getLocale(getLocaleContext());
   }

   public static Locale getLocale(@Nullable LocaleContext localeContext) {
      if (localeContext != null) {
         Locale locale = localeContext.getLocale();
         if (locale != null) {
            return locale;
         }
      }

      return defaultLocale != null ? defaultLocale : Locale.getDefault();
   }

   public static void setTimeZone(@Nullable TimeZone timeZone) {
      setTimeZone(timeZone, false);
   }

   public static void setTimeZone(@Nullable TimeZone timeZone, boolean inheritable) {
      LocaleContext localeContext = getLocaleContext();
      Locale locale = localeContext != null ? localeContext.getLocale() : null;
      if (timeZone != null) {
         localeContext = new SimpleTimeZoneAwareLocaleContext(locale, timeZone);
      } else if (locale != null) {
         localeContext = new SimpleLocaleContext(locale);
      } else {
         localeContext = null;
      }

      setLocaleContext(localeContext, inheritable);
   }

   public static void setDefaultTimeZone(@Nullable TimeZone timeZone) {
      defaultTimeZone = timeZone;
   }

   public static TimeZone getTimeZone() {
      return getTimeZone(getLocaleContext());
   }

   public static TimeZone getTimeZone(@Nullable LocaleContext localeContext) {
      if (localeContext instanceof TimeZoneAwareLocaleContext timeZoneAware) {
         TimeZone timeZone = timeZoneAware.getTimeZone();
         if (timeZone != null) {
            return timeZone;
         }
      }

      return defaultTimeZone != null ? defaultTimeZone : TimeZone.getDefault();
   }
}
