package org.springframework.boot.logging.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.DynamicConverter;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.logging.CorrelationIdFormatter;

public class CorrelationIdConverter extends DynamicConverter<ILoggingEvent> {
   private @Nullable CorrelationIdFormatter formatter;

   public void start() {
      this.formatter = CorrelationIdFormatter.of(this.getOptionList());
      super.start();
   }

   public void stop() {
      this.formatter = null;
      super.stop();
   }

   public String convert(ILoggingEvent event) {
      if (this.formatter == null) {
         return "";
      }

      Map<String, String> mdc = event.getMDCPropertyMap();
      return this.formatter.format(mdc::get);
   }
}
