package org.springframework.boot.logging.log4j2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;
import org.apache.logging.log4j.core.pattern.PatternFormatter;
import org.apache.logging.log4j.core.pattern.PatternParser;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.ansi.AnsiBackground;
import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiElement;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.boot.ansi.AnsiStyle;

@Plugin(name = "color", category = "Converter")
@ConverterKeys({"clr", "color"})
public final class ColorConverter extends LogEventPatternConverter {
   private static final Map<String, AnsiElement> ELEMENTS;
   private static final Map<Integer, AnsiElement> LEVELS;
   private final List<PatternFormatter> formatters;
   private final List<AnsiElement> stylings;

   private ColorConverter(List<PatternFormatter> formatters, List<AnsiElement> stylings) {
      super("style", "style");
      this.formatters = formatters;
      this.stylings = stylings;
   }

   public boolean handlesThrowable() {
      for (PatternFormatter formatter : this.formatters) {
         if (formatter.handlesThrowable()) {
            return true;
         }
      }

      return super.handlesThrowable();
   }

   public void format(LogEvent event, StringBuilder toAppendTo) {
      StringBuilder buf = new StringBuilder();

      for (PatternFormatter formatter : this.formatters) {
         formatter.format(event, buf);
      }

      if (!buf.isEmpty()) {
         if (this.stylings.isEmpty()) {
            AnsiElement element = LEVELS.get(event.getLevel().intLevel());
            element = element != null ? element : AnsiColor.GREEN;
            this.appendAnsiString(toAppendTo, buf.toString(), element);
         } else {
            this.appendAnsiString(toAppendTo, buf.toString(), this.stylings.toArray(new AnsiElement[0]));
         }
      }
   }

   private void appendAnsiString(StringBuilder toAppendTo, String in, AnsiElement element) {
      toAppendTo.append(AnsiOutput.toString(element, in));
   }

   void appendAnsiString(StringBuilder toAppendTo, String in, AnsiElement... elements) {
      Object[] ansiParams = new Object[elements.length + 1];
      System.arraycopy(elements, 0, ansiParams, 0, elements.length);
      ansiParams[elements.length] = in;
      toAppendTo.append(AnsiOutput.toString(ansiParams));
   }

   public static @Nullable ColorConverter newInstance(@Nullable Configuration config, @Nullable String[] options) {
      if (options.length < 1) {
         LOGGER.error("Incorrect number of options on style. Expected at least 1, received {}", options.length);
         return null;
      }

      if (options[0] == null) {
         LOGGER.error("No pattern supplied on style");
         return null;
      }

      PatternParser parser = PatternLayout.createPatternParser(config);
      List<PatternFormatter> formatters = parser.parse(options[0]);
      List<AnsiElement> stylings = new ArrayList<>();
      if (options.length >= 2 && options[1] != null) {
         String[] optionParts = options[1].split(",");

         for (String optionPart : optionParts) {
            AnsiElement element = ELEMENTS.get(optionPart.trim().toLowerCase(Locale.ROOT));
            if (element != null) {
               stylings.add(element);
            }
         }
      }

      return new ColorConverter(formatters, stylings);
   }

   static {
      Map<String, AnsiElement> ansiElements = new HashMap<>();
      Arrays.stream(AnsiColor.values())
         .filter(color -> color != AnsiColor.DEFAULT)
         .forEach(color -> ansiElements.put(color.name().toLowerCase(Locale.ROOT), color));
      Arrays.stream(AnsiStyle.values()).forEach(style -> ansiElements.put(style.name().toLowerCase(Locale.ROOT), style));
      Arrays.stream(AnsiBackground.values())
         .filter(bg -> bg != AnsiBackground.DEFAULT)
         .forEach(bg -> ansiElements.put("bg_" + bg.name().toLowerCase(Locale.ROOT), bg));
      ELEMENTS = Collections.unmodifiableMap(ansiElements);
      ansiElements = new HashMap<>();
      ansiElements.put(Level.FATAL.intLevel(), AnsiColor.RED);
      ansiElements.put(Level.ERROR.intLevel(), AnsiColor.RED);
      ansiElements.put(Level.WARN.intLevel(), AnsiColor.YELLOW);
      LEVELS = Collections.unmodifiableMap(ansiElements);
   }
}
