package org.springframework.boot.logging.log4j2;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;
import org.apache.logging.log4j.util.PerformanceSensitive;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.logging.CorrelationIdFormatter;
import org.springframework.util.ObjectUtils;

@Plugin(name = "CorrelationIdConverter", category = "Converter")
@ConverterKeys("correlationId")
@PerformanceSensitive("allocation")
public final class CorrelationIdConverter extends LogEventPatternConverter {
   private final CorrelationIdFormatter formatter;

   private CorrelationIdConverter(CorrelationIdFormatter formatter) {
      super("correlationId{%s}".formatted(formatter), "mdc");
      this.formatter = formatter;
   }

   public void format(LogEvent event, StringBuilder toAppendTo) {
      ReadOnlyStringMap contextData = event.getContextData();
      this.formatter.formatTo(contextData::getValue, toAppendTo);
   }

   public static CorrelationIdConverter newInstance(String @Nullable [] options) {
      String pattern = !ObjectUtils.isEmpty(options) ? options[0] : null;
      return new CorrelationIdConverter(CorrelationIdFormatter.of(pattern));
   }
}
