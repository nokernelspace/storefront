package org.springframework.boot.logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import org.springframework.boot.ansi.AnsiBackground;
import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiElement;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.boot.ansi.AnsiStyle;

public class ColorConverter extends CompositeConverter<ILoggingEvent> {
   private static final Map<String, AnsiElement> ELEMENTS;
   private static final Map<Integer, AnsiElement> LEVELS;

   protected String transform(ILoggingEvent event, String in) {
      List<String> options = this.getOptionList();
      List<AnsiElement> elements = new ArrayList<>();
      if (options != null) {
         for (String option : options) {
            String[] optionParts = option.split(",");

            for (String optionPart : optionParts) {
               AnsiElement element = ELEMENTS.get(optionPart.trim().toLowerCase(Locale.ROOT));
               if (element != null) {
                  elements.add(element);
               }
            }
         }
      }

      if (elements.isEmpty()) {
         AnsiElement element = LEVELS.get(event.getLevel().toInteger());
         elements.add(element != null ? element : AnsiColor.GREEN);
      }

      return this.toAnsiString(in, elements.toArray(new AnsiElement[0]));
   }

   protected String toAnsiString(String in, AnsiElement element) {
      return AnsiOutput.toString(element, in);
   }

   protected String toAnsiString(String in, AnsiElement... elements) {
      Object[] ansiParams = new Object[elements.length + 1];
      System.arraycopy(elements, 0, ansiParams, 0, elements.length);
      ansiParams[elements.length] = in;
      return AnsiOutput.toString(ansiParams);
   }

   static String getName(AnsiElement element) {
      return ELEMENTS.entrySet().stream().filter(entry -> entry.getValue().equals(element)).map(Entry::getKey).findFirst().orElseThrow();
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
      ansiElements.put(Level.ERROR_INTEGER, AnsiColor.RED);
      ansiElements.put(Level.WARN_INTEGER, AnsiColor.YELLOW);
      LEVELS = Collections.unmodifiableMap(ansiElements);
   }
}
