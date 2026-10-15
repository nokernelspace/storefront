package org.springframework.context;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

public interface MessageSource {
   @Nullable String getMessage(String code, Object @Nullable [] args, @Nullable String defaultMessage, @Nullable Locale locale);

   String getMessage(String code, Object @Nullable [] args, @Nullable Locale locale) throws NoSuchMessageException;

   String getMessage(MessageSourceResolvable resolvable, @Nullable Locale locale) throws NoSuchMessageException;
}
