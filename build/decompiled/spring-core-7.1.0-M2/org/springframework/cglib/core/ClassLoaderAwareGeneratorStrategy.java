package org.springframework.cglib.core;

public class ClassLoaderAwareGeneratorStrategy extends DefaultGeneratorStrategy {
   private final ClassLoader classLoader;
   private final GeneratorStrategy delegate;

   public ClassLoaderAwareGeneratorStrategy(ClassLoader classLoader) {
      this.classLoader = classLoader;
      this.delegate = x$0 -> super.generate(x$0);
   }

   public ClassLoaderAwareGeneratorStrategy(ClassLoader classLoader, GeneratorStrategy delegate) {
      this.classLoader = classLoader;
      this.delegate = delegate;
   }

   @Override
   public byte[] generate(ClassGenerator cg) throws Exception {
      if (this.classLoader == null) {
         return this.delegate.generate(cg);
      }

      Thread currentThread = Thread.currentThread();

      ClassLoader threadContextClassLoader;
      try {
         threadContextClassLoader = currentThread.getContextClassLoader();
      } catch (Throwable ex) {
         return this.delegate.generate(cg);
      }

      boolean overrideClassLoader = !this.classLoader.equals(threadContextClassLoader);
      if (overrideClassLoader) {
         currentThread.setContextClassLoader(this.classLoader);
      }

      try {
         return this.delegate.generate(cg);
      } finally {
         if (overrideClassLoader) {
            currentThread.setContextClassLoader(threadContextClassLoader);
         }
      }
   }
}
