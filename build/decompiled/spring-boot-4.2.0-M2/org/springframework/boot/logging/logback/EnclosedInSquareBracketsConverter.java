package org.springframework.boot.logging.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

public class EnclosedInSquareBracketsConverter extends CompositeConverter<ILoggingEvent> {
   protected String transform(ILoggingEvent event, @Nullable String in) {
      in = !StringUtils.hasLength(in) ? this.resolveFromFirstOption(event) : in;
      return !StringUtils.hasLength(in) ? "" : "[%s] ".formatted(in);
   }

   private @Nullable String resolveFromFirstOption(ILoggingEvent event) {
      String name = this.getFirstOption();
      if (name == null) {
         return null;
      }

      String value = (String)event.getLoggerContextVO().getPropertyMap().get(name);
      return value != null ? value : System.getProperty(name);
   }
}
