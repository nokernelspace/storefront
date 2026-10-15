package org.springframework.instrument.classloading;

import java.lang.instrument.ClassFileTransformer;
import org.jspecify.annotations.Nullable;
import org.springframework.core.OverridingClassLoader;

public class SimpleInstrumentableClassLoader extends OverridingClassLoader {
   private final WeavingTransformer weavingTransformer;

   public SimpleInstrumentableClassLoader(@Nullable ClassLoader parent) {
      super(parent);
      this.weavingTransformer = new WeavingTransformer(parent);
   }

   public void addTransformer(ClassFileTransformer transformer) {
      this.weavingTransformer.addTransformer(transformer);
   }

   protected byte[] transformIfNecessary(String name, byte[] bytes) {
      return this.weavingTransformer.transformIfNecessary(name, bytes);
   }

   static {
      ClassLoader.registerAsParallelCapable();
   }
}
