package org.springframework.boot.logging.log4j2;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.ExtendedThrowablePatternConverter;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;
import org.jspecify.annotations.Nullable;

@Plugin(name = "ExtendedWhitespaceThrowablePatternConverter", category = "Converter")
@ConverterKeys({"xwEx", "xwThrowable", "xwException"})
public final class ExtendedWhitespaceThrowablePatternConverter extends LogEventPatternConverter {
   private final ExtendedThrowablePatternConverter delegate;
   private final String separator;

   private ExtendedWhitespaceThrowablePatternConverter(Configuration configuration, @Nullable String[] options) {
      super("WhitespaceExtendedThrowable", "throwable");
      this.delegate = ExtendedThrowablePatternConverter.newInstance(configuration, options);
      this.separator = this.delegate.getOptions().getSeparator();
   }

   public void format(LogEvent event, StringBuilder buffer) {
      if (event.getThrown() != null) {
         buffer.append(this.separator);
         this.delegate.format(event, buffer);
         buffer.append(this.separator);
      }
   }

   public boolean handlesThrowable() {
      return true;
   }

   public static ExtendedWhitespaceThrowablePatternConverter newInstance(Configuration configuration, @Nullable String[] options) {
      return new ExtendedWhitespaceThrowablePatternConverter(configuration, options);
   }
}
