package org.springframework.util;

import org.jspecify.annotations.Nullable;

public abstract class SystemPropertyUtils {
   public static final String PLACEHOLDER_PREFIX = "${";
   public static final String PLACEHOLDER_SUFFIX = "}";
   public static final String VALUE_SEPARATOR = ":";
   public static final Character ESCAPE_CHARACTER = '\\';
   private static final PropertyPlaceholderHelper strictHelper = new PropertyPlaceholderHelper("${", "}", ":", ESCAPE_CHARACTER, false);
   private static final PropertyPlaceholderHelper nonStrictHelper = new PropertyPlaceholderHelper("${", "}", ":", ESCAPE_CHARACTER, true);

   public static String resolvePlaceholders(String text) {
      return resolvePlaceholders(text, false);
   }

   public static String resolvePlaceholders(String text, boolean ignoreUnresolvablePlaceholders) {
      if (text.isEmpty()) {
         return text;
      }

      PropertyPlaceholderHelper helper = ignoreUnresolvablePlaceholders ? nonStrictHelper : strictHelper;
      return helper.replacePlaceholders(text, new SystemPropertyUtils.SystemPropertyPlaceholderResolver(text));
   }

   private static class SystemPropertyPlaceholderResolver implements PropertyPlaceholderHelper.PlaceholderResolver {
      private final String text;

      public SystemPropertyPlaceholderResolver(String text) {
         this.text = text;
      }

      @Override
      public @Nullable String resolvePlaceholder(String placeholderName) {
         try {
            String propVal = System.getProperty(placeholderName);
            if (propVal == null) {
               propVal = System.getenv(placeholderName);
            }

            return propVal;
         } catch (Throwable ex) {
            System.err.println("Could not resolve placeholder '" + placeholderName + "' in [" + this.text + "] as system property: " + ex);
            return null;
         }
      }
   }
}
