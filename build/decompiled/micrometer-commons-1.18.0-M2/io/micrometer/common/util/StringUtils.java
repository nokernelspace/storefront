package io.micrometer.common.util;

import io.micrometer.common.lang.internal.Contract;
import org.jspecify.annotations.Nullable;

public final class StringUtils {
   @Contract("null -> true")
   public static boolean isBlank(@Nullable String string) {
      if (isEmpty(string)) {
         return true;
      }

      for (int i = 0; i < string.length(); i++) {
         if (!Character.isWhitespace(string.charAt(i))) {
            return false;
         }
      }

      return true;
   }

   @Contract("null -> false")
   public static boolean isNotBlank(@Nullable String string) {
      return !isBlank(string);
   }

   @Contract("null -> true")
   public static boolean isEmpty(@Nullable String string) {
      return string == null || string.isEmpty();
   }

   @Contract("null -> false")
   public static boolean isNotEmpty(@Nullable String string) {
      return !isEmpty(string);
   }

   public static String truncate(String string, int maxLength) {
      return string.length() > maxLength ? string.substring(0, maxLength) : string;
   }

   public static String truncate(String string, int maxLength, String truncationIndicator) {
      if (truncationIndicator.length() >= maxLength) {
         throw new IllegalArgumentException("maxLength must be greater than length of truncationIndicator");
      } else if (string.length() > maxLength) {
         int remainingLength = maxLength - truncationIndicator.length();
         return string.substring(0, remainingLength) + truncationIndicator;
      } else {
         return string;
      }
   }

   private StringUtils() {
   }
}
