package org.springframework.boot.logging.log4j2;

import java.util.List;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;
import org.apache.logging.log4j.core.pattern.PatternFormatter;
import org.apache.logging.log4j.core.pattern.PatternParser;
import org.jspecify.annotations.Nullable;

@Plugin(name = "enclosedInSquareBrackets", category = "Converter")
@ConverterKeys("esb")
public final class EnclosedInSquareBracketsConverter extends LogEventPatternConverter {
   private final List<PatternFormatter> formatters;

   private EnclosedInSquareBracketsConverter(List<PatternFormatter> formatters) {
      super("enclosedInSquareBrackets", null);
      this.formatters = formatters;
   }

   public void format(LogEvent event, StringBuilder toAppendTo) {
      StringBuilder buf = new StringBuilder();

      for (PatternFormatter formatter : this.formatters) {
         formatter.format(event, buf);
      }

      if (!buf.isEmpty()) {
         toAppendTo.append("[");
         toAppendTo.append(buf);
         toAppendTo.append("] ");
      }
   }

   public static @Nullable EnclosedInSquareBracketsConverter newInstance(@Nullable Configuration config, String[] options) {
      if (options.length < 1) {
         LOGGER.error("Incorrect number of options on style. Expected at least 1, received {}", options.length);
         return null;
      } else {
         PatternParser parser = PatternLayout.createPatternParser(config);
         List<PatternFormatter> formatters = parser.parse(options[0]);
         return new EnclosedInSquareBracketsConverter(formatters);
      }
   }
}
