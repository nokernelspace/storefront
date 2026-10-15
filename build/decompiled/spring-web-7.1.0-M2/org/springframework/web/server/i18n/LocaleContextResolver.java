package org.springframework.web.server.i18n;

import org.jspecify.annotations.Nullable;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.web.server.ServerWebExchange;

public interface LocaleContextResolver {
   LocaleContext resolveLocaleContext(ServerWebExchange exchange);

   void setLocaleContext(ServerWebExchange exchange, @Nullable LocaleContext localeContext);
}
