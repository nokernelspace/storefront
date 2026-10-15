package org.springframework.context.i18n;

import java.util.TimeZone;
import org.jspecify.annotations.Nullable;

public interface TimeZoneAwareLocaleContext extends LocaleContext {
   @Nullable TimeZone getTimeZone();
}
