package org.springframework.context.i18n;

import io.micrometer.context.ThreadLocalAccessor;
import org.jspecify.annotations.Nullable;

public class LocaleContextThreadLocalAccessor implements ThreadLocalAccessor<LocaleContext> {
   public static final String KEY = LocaleContextThreadLocalAccessor.class.getName() + ".KEY";

   public Object key() {
      return KEY;
   }

   public @Nullable LocaleContext getValue() {
      return LocaleContextHolder.getLocaleContext();
   }

   public void setValue(LocaleContext value) {
      LocaleContextHolder.setLocaleContext(value);
   }

   public void setValue() {
      LocaleContextHolder.resetLocaleContext();
   }
}
