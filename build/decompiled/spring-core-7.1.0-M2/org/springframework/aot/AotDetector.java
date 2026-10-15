package org.springframework.aot;

import org.springframework.core.NativeDetector;
import org.springframework.core.SpringProperties;

public abstract class AotDetector {
   public static final String AOT_ENABLED = "spring.aot.enabled";
   private static final boolean inNativeImage = NativeDetector.inNativeImage(NativeDetector.Context.RUN, NativeDetector.Context.BUILD);

   public static boolean useGeneratedArtifacts() {
      return inNativeImage || SpringProperties.getFlag("spring.aot.enabled");
   }
}
