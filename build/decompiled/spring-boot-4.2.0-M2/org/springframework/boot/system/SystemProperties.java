package org.springframework.boot.system;

import org.jspecify.annotations.Nullable;

public final class SystemProperties {
   private SystemProperties() {
   }

   public static @Nullable String get(String... properties) {
      for (String property : properties) {
         try {
            String override = System.getProperty(property);
            override = override != null ? override : System.getenv(property);
            if (override != null) {
               return override;
            }
         } catch (Throwable ex) {
            System.err.println("Could not resolve '" + property + "' as system property: " + ex);
         }
      }

      return null;
   }
}
