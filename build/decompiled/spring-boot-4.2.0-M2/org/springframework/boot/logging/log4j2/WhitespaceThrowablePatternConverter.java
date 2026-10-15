package org.springframework.boot.logging.log4j2;

import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;
import org.apache.logging.log4j.core.pattern.VariablesNotEmptyReplacementConverter;
import org.jspecify.annotations.Nullable;

@Plugin(name = "WhitespaceThrowablePatternConverter", category = "Converter")
@ConverterKeys({"wEx", "wThrowable", "wException"})
public final class WhitespaceThrowablePatternConverter {
   private WhitespaceThrowablePatternConverter() {
   }

   public static LogEventPatternConverter newInstance(Configuration configuration, String @Nullable [] options) {
      String nested = options != null && options.length > 0 ? "{" + String.join("}{", options) + "}" : "";
      String pattern = "%notEmpty{%n%ex" + nested + "%n}";
      return VariablesNotEmptyReplacementConverter.newInstance(configuration, new String[]{pattern});
   }
}
