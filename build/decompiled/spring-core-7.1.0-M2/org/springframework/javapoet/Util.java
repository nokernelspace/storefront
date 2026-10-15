package org.springframework.javapoet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import javax.lang.model.element.Modifier;

final class Util {
   private Util() {
   }

   static <K, V> Map<K, List<V>> immutableMultimap(Map<K, List<V>> multimap) {
      Map<K, List<V>> result = new LinkedHashMap<>();

      for (Entry<K, List<V>> entry : multimap.entrySet()) {
         if (!entry.getValue().isEmpty()) {
            result.put(entry.getKey(), immutableList(entry.getValue()));
         }
      }

      return Collections.unmodifiableMap(result);
   }

   static <K, V> Map<K, V> immutableMap(Map<K, V> map) {
      return Collections.unmodifiableMap(new LinkedHashMap<>(map));
   }

   static void checkArgument(boolean condition, String format, Object... args) {
      if (!condition) {
         throw new IllegalArgumentException(String.format(format, args));
      }
   }

   static <T> T checkNotNull(T reference, String format, Object... args) {
      if (reference == null) {
         throw new NullPointerException(String.format(format, args));
      } else {
         return reference;
      }
   }

   static void checkState(boolean condition, String format, Object... args) {
      if (!condition) {
         throw new IllegalStateException(String.format(format, args));
      }
   }

   static <T> List<T> immutableList(Collection<T> collection) {
      return Collections.unmodifiableList(new ArrayList<>(collection));
   }

   static <T> Set<T> immutableSet(Collection<T> set) {
      return Collections.unmodifiableSet(new LinkedHashSet<>(set));
   }

   static <T> Set<T> union(Set<T> a, Set<T> b) {
      Set<T> result = new LinkedHashSet<>();
      result.addAll(a);
      result.addAll(b);
      return result;
   }

   static void requireExactlyOneOf(Set<Modifier> modifiers, Modifier... mutuallyExclusive) {
      int count = 0;

      for (Modifier modifier : mutuallyExclusive) {
         if (modifiers.contains(modifier)) {
            count++;
         }
      }

      checkArgument(count == 1, "modifiers %s must contain one of %s", modifiers, Arrays.toString(mutuallyExclusive));
   }

   static String characterLiteralWithoutSingleQuotes(char c) {
      return switch (c) {
         case '\b' -> "\\b";
         case '\t' -> "\\t";
         case '\n' -> "\\n";
         case '\f' -> "\\f";
         case '\r' -> "\\r";
         case '"' -> "\"";
         case '\'' -> "\\'";
         case '\\' -> "\\\\";
         default -> Character.isISOControl(c) ? String.format("\\u%04x", Integer.valueOf(c)) : Character.toString(c);
      };
   }

   static String stringLiteralWithDoubleQuotes(String value, String indent) {
      StringBuilder result = new StringBuilder(value.length() + 2);
      result.append('"');

      for (int i = 0; i < value.length(); i++) {
         char c = value.charAt(i);
         if (c == '\'') {
            result.append("'");
         } else if (c == '"') {
            result.append("\\\"");
         } else {
            result.append(characterLiteralWithoutSingleQuotes(c));
            if (c == '\n' && i + 1 < value.length()) {
               result.append("\"\n").append(indent).append(indent).append("+ \"");
            }
         }
      }

      result.append('"');
      return result.toString();
   }
}
