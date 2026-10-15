package org.springframework.beans;

import org.jspecify.annotations.Nullable;

@Deprecated(since = "7.1", forRemoval = true)
public abstract class PropertyAccessorUtils {
   public static String getPropertyName(String propertyPath) {
      int separatorIndex = propertyPath.endsWith("]") ? propertyPath.indexOf(91) : -1;
      return separatorIndex != -1 ? propertyPath.substring(0, separatorIndex) : propertyPath;
   }

   public static boolean isNestedOrIndexedProperty(@Nullable String propertyPath) {
      if (propertyPath == null) {
         return false;
      }

      for (int i = 0; i < propertyPath.length(); i++) {
         char ch = propertyPath.charAt(i);
         if (ch == '.' || ch == '[') {
            return true;
         }
      }

      return false;
   }

   public static int getFirstNestedPropertySeparatorIndex(String propertyPath) {
      return getNestedPropertySeparatorIndex(propertyPath, false);
   }

   public static int getLastNestedPropertySeparatorIndex(String propertyPath) {
      return getNestedPropertySeparatorIndex(propertyPath, true);
   }

   private static int getNestedPropertySeparatorIndex(String propertyPath, boolean last) {
      int depth = 0;
      int length = propertyPath.length();
      int i = last ? length - 1 : 0;

      while (last ? i >= 0 : i < length) {
         switch (propertyPath.charAt(i)) {
            case '.':
               if (depth <= 0) {
                  return i;
               }
               break;
            case '[':
               depth += last ? -1 : 1;
               break;
            case ']':
               depth += last ? 1 : -1;
         }

         if (last) {
            i--;
         } else {
            i++;
         }
      }

      return -1;
   }

   public static boolean matchesProperty(String registeredPath, String propertyPath) {
      String registered = canonicalPropertyName(registeredPath);
      String property = canonicalPropertyName(propertyPath);
      if (!registered.startsWith(property)) {
         return false;
      } else if (registered.length() == property.length()) {
         return true;
      } else {
         return registered.charAt(property.length()) != '[' ? false : registered.indexOf(93, property.length() + 1) == registered.length() - 1;
      }
   }

   public static String canonicalPropertyName(@Nullable String propertyName) {
      return PropertyPath.canonicalNameOrOriginal(propertyName);
   }

   public static String @Nullable [] canonicalPropertyNames(String @Nullable [] propertyNames) {
      if (propertyNames == null) {
         return null;
      }

      String[] result = new String[propertyNames.length];

      for (int i = 0; i < propertyNames.length; i++) {
         result[i] = canonicalPropertyName(propertyNames[i]);
      }

      return result;
   }
}
