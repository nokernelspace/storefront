package org.springframework.core;

import org.jspecify.annotations.Nullable;

public abstract class NativeDetector {
   private static final @Nullable String imageCode = System.getProperty("org.graalvm.nativeimage.imagecode");
   private static final boolean inNativeImage = imageCode != null;

   public static boolean inNativeImage() {
      return inNativeImage;
   }

   public static boolean inNativeImage(NativeDetector.Context... contexts) {
      for (NativeDetector.Context context : contexts) {
         if (context.key.equals(imageCode)) {
            return true;
         }
      }

      return false;
   }

   public enum Context {
      BUILD("buildtime"),
      RUN("runtime");

      private final String key;

      Context(final String key) {
         this.key = key;
      }

      @Override
      public String toString() {
         return this.key;
      }
   }
}
