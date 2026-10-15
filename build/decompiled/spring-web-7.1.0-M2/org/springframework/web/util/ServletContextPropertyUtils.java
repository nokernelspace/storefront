package org.springframework.web.util;

import jakarta.servlet.ServletContext;
import org.jspecify.annotations.Nullable;
import org.springframework.util.PropertyPlaceholderHelper;
import org.springframework.util.SystemPropertyUtils;
import org.springframework.util.PropertyPlaceholderHelper.PlaceholderResolver;

public abstract class ServletContextPropertyUtils {
   private static final PropertyPlaceholderHelper strictHelper = new PropertyPlaceholderHelper("${", "}", ":", SystemPropertyUtils.ESCAPE_CHARACTER, false);
   private static final PropertyPlaceholderHelper nonStrictHelper = new PropertyPlaceholderHelper("${", "}", ":", SystemPropertyUtils.ESCAPE_CHARACTER, true);

   public static String resolvePlaceholders(String text, ServletContext servletContext) {
      return resolvePlaceholders(text, servletContext, false);
   }

   public static String resolvePlaceholders(String text, ServletContext servletContext, boolean ignoreUnresolvablePlaceholders) {
      if (text.isEmpty()) {
         return text;
      }

      PropertyPlaceholderHelper helper = ignoreUnresolvablePlaceholders ? nonStrictHelper : strictHelper;
      return helper.replacePlaceholders(text, new ServletContextPropertyUtils.ServletContextPlaceholderResolver(text, servletContext));
   }

   private static class ServletContextPlaceholderResolver implements PlaceholderResolver {
      private final String text;
      private final ServletContext servletContext;

      public ServletContextPlaceholderResolver(String text, ServletContext servletContext) {
         this.text = text;
         this.servletContext = servletContext;
      }

      public @Nullable String resolvePlaceholder(String placeholderName) {
         try {
            String propVal = this.servletContext.getInitParameter(placeholderName);
            if (propVal == null) {
               propVal = System.getProperty(placeholderName);
               if (propVal == null) {
                  propVal = System.getenv(placeholderName);
               }
            }

            return propVal;
         } catch (Throwable ex) {
            System.err
               .println(
                  "Could not resolve placeholder '" + placeholderName + "' in [" + this.text + "] as ServletContext init-parameter or system property: " + ex
               );
            return null;
         }
      }
   }
}
