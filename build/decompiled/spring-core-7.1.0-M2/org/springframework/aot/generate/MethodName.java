package org.springframework.aot.generate;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

final class MethodName {
   private static final String[] PREFIXES = new String[]{"get", "set", "is"};
   public static final MethodName NONE = of();
   private final String value;

   private MethodName(String value) {
      this.value = value;
   }

   static MethodName of(String... parts) {
      Assert.notNull(parts, "'parts' must not be null");
      return new MethodName(join(parts));
   }

   MethodName and(MethodName name) {
      Assert.notNull(name, "'name' must not be null");
      return this.and(name.value);
   }

   MethodName and(String... parts) {
      Assert.notNull(parts, "'parts' must not be null");
      String joined = join(parts);
      String prefix = this.getPrefix(joined);
      String suffix = joined.substring(prefix.length());
      return of(prefix, this.value, suffix);
   }

   private String getPrefix(String name) {
      for (String candidate : PREFIXES) {
         if (name.startsWith(candidate)) {
            return candidate;
         }
      }

      return "";
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof MethodName that && this.value.equals(that.value);
   }

   @Override
   public int hashCode() {
      return this.value.hashCode();
   }

   @Override
   public String toString() {
      return !StringUtils.hasLength(this.value) ? "$$aot" : this.value;
   }

   private static String join(String[] parts) {
      return StringUtils.uncapitalize(Arrays.stream(parts).map(MethodName::clean).map(StringUtils::capitalize).collect(Collectors.joining()));
   }

   private static String clean(@Nullable String part) {
      char[] chars = part != null ? part.toCharArray() : new char[0];
      StringBuilder name = new StringBuilder(chars.length);
      boolean uppercase = false;

      for (char ch : chars) {
         char outputChar = !uppercase ? ch : Character.toUpperCase(ch);
         name.append(!Character.isLetter(ch) ? "" : outputChar);
         uppercase = ch == '.';
      }

      return name.toString();
   }
}
