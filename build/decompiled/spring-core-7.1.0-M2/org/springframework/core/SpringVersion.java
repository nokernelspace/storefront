package org.springframework.core;

import org.jspecify.annotations.Nullable;

public final class SpringVersion {
   private SpringVersion() {
   }

   public static @Nullable String getVersion() {
      Package pkg = SpringVersion.class.getPackage();
      String version = pkg != null ? pkg.getImplementationVersion() : null;
      if (version != null) {
         int idx = version.indexOf(46);
         if (idx != -1) {
            idx = version.indexOf(46, idx + 1);
            if (idx != -1) {
               idx = version.indexOf(46, idx + 1);
               if (idx != -1) {
                  version = version.substring(0, idx);
               }
            }
         }
      }

      return version;
   }
}
