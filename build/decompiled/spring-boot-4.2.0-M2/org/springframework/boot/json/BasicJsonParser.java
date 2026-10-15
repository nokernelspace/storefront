package org.springframework.boot.json;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class BasicJsonParser extends AbstractJsonParser {
   private static final int MAX_DEPTH = 1000;

   @Override
   public Map<String, Object> parseMap(@Nullable String json) {
      return this.tryParse(() -> this.parseMap(json, jsonToParse -> this.parseMapInternal(0, jsonToParse)), Exception.class);
   }

   @Override
   public List<Object> parseList(@Nullable String json) {
      return this.tryParse(() -> this.parseList(json, jsonToParse -> this.parseListInternal(0, jsonToParse)), Exception.class);
   }

   private List<Object> parseListInternal(int nesting, String json) {
      List<Object> list = new ArrayList<>();
      json = trimEdges(json, '[', ']').trim();

      for (String value : this.tokenize(json)) {
         list.add(this.parseInternal(nesting + 1, value));
      }

      return list;
   }

   private Object parseInternal(int nesting, String json) {
      if (nesting > 1000) {
         throw new IllegalStateException("JSON is too deeply nested");
      } else if (json.startsWith("[")) {
         return this.parseListInternal(nesting + 1, json);
      } else if (json.startsWith("{")) {
         return this.parseMapInternal(nesting + 1, json);
      } else {
         return json.startsWith("\"") ? trimEdges(json, '"', '"') : this.parseNumber(json);
      }
   }

   private Map<String, Object> parseMapInternal(int nesting, String json) {
      Map<String, Object> map = new LinkedHashMap<>();
      json = trimEdges(json, '{', '}').trim();

      for (String pair : this.tokenize(json)) {
         String[] split = StringUtils.split(pair, ":");
         Assert.state(split != null, () -> "Unable to parse '%s'".formatted(pair));
         String[] rawElement = StringUtils.trimArrayElements(split);
         String rawKey = rawElement[0];
         String rawValue = rawElement[1];
         Assert.state(rawKey != null, () -> "rawKey is null in '%s'".formatted(pair));
         Assert.state(rawKey.startsWith("\"") && rawKey.endsWith("\""), "Expecting double-quotes around field names");
         String key = trimEdges(rawKey, '"', '"');
         Assert.state(rawValue != null, () -> "rawValue is null in '%s'".formatted(pair));
         Object value = this.parseInternal(nesting, rawValue);
         map.put(key, value);
      }

      return map;
   }

   private Object parseNumber(String json) {
      try {
         return Long.valueOf(json);
      } catch (NumberFormatException ex) {
         try {
            return Double.valueOf(json);
         } catch (NumberFormatException ex2) {
            return json;
         }
      }
   }

   private static String trimTrailingCharacter(String string, char c) {
      return !string.isEmpty() && string.charAt(string.length() - 1) == c ? string.substring(0, string.length() - 1) : string;
   }

   private static String trimLeadingCharacter(String string, char c) {
      return !string.isEmpty() && string.charAt(0) == c ? string.substring(1) : string;
   }

   private static String trimEdges(String string, char leadingChar, char trailingChar) {
      return trimTrailingCharacter(trimLeadingCharacter(string, leadingChar), trailingChar);
   }

   private List<String> tokenize(String json) {
      List<String> list = new ArrayList<>();
      BasicJsonParser.Tracking tracking = new BasicJsonParser.Tracking();
      StringBuilder build = new StringBuilder();
      int index = 0;

      while (index < json.length()) {
         char ch = json.charAt(index);
         if (tracking.in(BasicJsonParser.Tracked.ESCAPE)) {
            build.append(ch);
            index++;
            tracking.set(BasicJsonParser.Tracked.ESCAPE, 0);
         } else {
            switch (ch) {
               case '"':
                  tracking.toggle(BasicJsonParser.Tracked.VALUE);
                  break;
               case '[':
                  tracking.update(BasicJsonParser.Tracked.LIST, 1);
                  break;
               case ']':
                  tracking.update(BasicJsonParser.Tracked.LIST, -1);
                  break;
               case '{':
                  tracking.update(BasicJsonParser.Tracked.OBJECT, 1);
                  break;
               case '}':
                  tracking.update(BasicJsonParser.Tracked.OBJECT, -1);
            }

            if (ch == ',' && !tracking.in(BasicJsonParser.Tracked.OBJECT, BasicJsonParser.Tracked.LIST, BasicJsonParser.Tracked.VALUE)) {
               list.add(build.toString());
               build.setLength(0);
            } else if (ch == '\\') {
               tracking.set(BasicJsonParser.Tracked.ESCAPE, 1);
            } else {
               build.append(ch);
            }

            index++;
         }
      }

      if (!build.isEmpty()) {
         list.add(build.toString().trim());
      }

      return list;
   }

   private enum Tracked {
      OBJECT,
      LIST,
      VALUE,
      ESCAPE;
   }

   private static final class Tracking {
      private final int[] counts = new int[BasicJsonParser.Tracked.values().length];

      boolean in(BasicJsonParser.Tracked... tracked) {
         return Arrays.stream(tracked).mapToInt(this::get).anyMatch(i -> i > 0);
      }

      void toggle(BasicJsonParser.Tracked tracked) {
         this.set(tracked, this.get(tracked) != 0 ? 0 : 1);
      }

      void update(BasicJsonParser.Tracked tracked, int delta) {
         this.set(tracked, this.get(tracked) + delta);
      }

      private int get(BasicJsonParser.Tracked tracked) {
         return this.counts[tracked.ordinal()];
      }

      void set(BasicJsonParser.Tracked tracked, int count) {
         this.counts[tracked.ordinal()] = count;
      }
   }
}
