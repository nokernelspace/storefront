package org.springframework.instrument.classloading;

import org.jspecify.annotations.Nullable;
import org.springframework.core.OverridingClassLoader;

public class SimpleThrowawayClassLoader extends OverridingClassLoader {
   public SimpleThrowawayClassLoader(@Nullable ClassLoader parent) {
      super(parent);
   }

   static {
      ClassLoader.registerAsParallelCapable();
   }
}
