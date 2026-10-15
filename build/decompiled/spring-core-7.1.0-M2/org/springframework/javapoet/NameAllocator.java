package org.springframework.javapoet;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.lang.model.SourceVersion;

public final class NameAllocator implements Cloneable {
   private final Set<String> allocatedNames;
   private final Map<Object, String> tagToName;

   public NameAllocator() {
      this(new LinkedHashSet<>(), new LinkedHashMap<>());
   }

   private NameAllocator(Set<String> allocatedNames, Map<Object, String> tagToName) {
      this.allocatedNames = allocatedNames;
      this.tagToName = tagToName;
   }

   public String newName(String suggestion) {
      return this.newName(suggestion, UUID.randomUUID().toString());
   }

   public String newName(String suggestion, Object tag) {
      Util.checkNotNull(suggestion, "suggestion");
      Util.checkNotNull(tag, "tag");
      suggestion = toJavaIdentifier(suggestion);

      while (SourceVersion.isKeyword(suggestion) || !this.allocatedNames.add(suggestion)) {
         suggestion = suggestion + "_";
      }

      String replaced = this.tagToName.put(tag, suggestion);
      if (replaced != null) {
         this.tagToName.put(tag, replaced);
         throw new IllegalArgumentException("tag " + tag + " cannot be used for both '" + replaced + "' and '" + suggestion + "'");
      } else {
         return suggestion;
      }
   }

   public static String toJavaIdentifier(String suggestion) {
      StringBuilder result = new StringBuilder();
      int i = 0;

      while (i < suggestion.length()) {
         int codePoint = suggestion.codePointAt(i);
         if (i == 0 && !Character.isJavaIdentifierStart(codePoint) && Character.isJavaIdentifierPart(codePoint)) {
            result.append("_");
         }

         int validCodePoint = Character.isJavaIdentifierPart(codePoint) ? codePoint : 95;
         result.appendCodePoint(validCodePoint);
         i += Character.charCount(codePoint);
      }

      return result.toString();
   }

   public String get(Object tag) {
      String result = this.tagToName.get(tag);
      if (result == null) {
         throw new IllegalArgumentException("unknown tag: " + tag);
      } else {
         return result;
      }
   }

   public NameAllocator clone() {
      return new NameAllocator(new LinkedHashSet<>(this.allocatedNames), new LinkedHashMap<>(this.tagToName));
   }
}
