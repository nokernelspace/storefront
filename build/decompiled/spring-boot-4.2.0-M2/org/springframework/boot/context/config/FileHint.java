package org.springframework.boot.context.config;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class FileHint {
   private static final FileHint NONE = new FileHint(Collections.emptyMap());
   private final Map<String, String> attributes;

   private FileHint(Map<String, String> attributes) {
      this.attributes = attributes;
   }

   boolean isPresent() {
      return this.getExtension() != null || this.getEncoding() != null;
   }

   @Nullable String getExtension() {
      String extension = this.attributes.get("extension");
      if (extension == null) {
         return null;
      } else {
         return !extension.startsWith(".") ? null : extension;
      }
   }

   String getExtensionOrElse(String fallback) {
      String extension = this.getExtension();
      return extension != null ? extension : fallback;
   }

   @Nullable String getEncoding() {
      return this.attributes.get("encoding");
   }

   @Nullable Charset getEncodingAsCharset() {
      String encoding = this.getEncoding();
      return encoding != null ? Charset.forName(encoding) : null;
   }

   @Override
   public String toString() {
      return "FileHint{attributes=" + this.attributes + "}";
   }

   static FileHint from(String value) {
      if (!hasBrackets(value)) {
         return NONE;
      }

      List<String> betweenBrackets = findBetweenBrackets(value);
      Map<String, String> attributes = new HashMap<>();

      for (String entry : betweenBrackets) {
         String cleaned = entry.trim();
         if (!cleaned.isEmpty()) {
            int equals = cleaned.indexOf(61);
            if (equals == -1) {
               attributes.put("extension", cleaned);
            } else {
               attributes.put(cleaned.substring(0, equals).trim(), cleaned.substring(equals + 1).trim());
            }
         }
      }

      return new FileHint(Collections.unmodifiableMap(attributes));
   }

   private static List<String> findBetweenBrackets(String value) {
      List<String> collected = new ArrayList<>();
      StringBuilder buffer = new StringBuilder();
      int brackets = 0;

      for (int i = 0; i < value.length(); i++) {
         char c = value.charAt(i);
         if (c == '[') {
            if (brackets > 0) {
               buffer.append(c);
            }

            brackets++;
         } else if (c == ']') {
            if (--brackets > 0) {
               buffer.append(c);
            } else if (brackets == 0 && !buffer.isEmpty()) {
               collected.add(buffer.toString());
               buffer.setLength(0);
            }
         } else if (brackets > 0) {
            buffer.append(c);
         }
      }

      return collected;
   }

   static String removeFrom(String value) {
      if (!hasBrackets(value)) {
         return value;
      }

      StringBuilder result = new StringBuilder();
      int brackets = 0;

      for (int i = 0; i < value.length(); i++) {
         char c = value.charAt(i);
         if (c == '[') {
            brackets++;
         } else if (c == ']') {
            brackets--;
         } else if (brackets <= 0) {
            result.append(c);
         }
      }

      return result.toString();
   }

   private static boolean hasBrackets(String value) {
      return value.indexOf(91) != -1 && value.indexOf(93) != -1;
   }
}
