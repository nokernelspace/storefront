package org.springframework.context.i18n;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

public interface LocaleContext {
   @Nullable Locale getLocale();
}
