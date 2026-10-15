package org.springframework.instrument.classloading.tomcat;

import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;
import org.springframework.core.OverridingClassLoader;
import org.springframework.instrument.classloading.LoadTimeWeaver;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

public class TomcatLoadTimeWeaver implements LoadTimeWeaver {
   private static final String INSTRUMENTABLE_LOADER_CLASS_NAME = "org.apache.tomcat.InstrumentableClassLoader";
   private final ClassLoader classLoader;
   private final Method addTransformerMethod;
   private final Method copyMethod;

   public TomcatLoadTimeWeaver() {
      this(ClassUtils.getDefaultClassLoader());
   }

   public TomcatLoadTimeWeaver(@Nullable ClassLoader classLoader) {
      Assert.notNull(classLoader, "ClassLoader must not be null");
      this.classLoader = classLoader;

      Class<?> instrumentableLoaderClass;
      try {
         instrumentableLoaderClass = classLoader.loadClass("org.apache.tomcat.InstrumentableClassLoader");
         if (!instrumentableLoaderClass.isInstance(classLoader)) {
            instrumentableLoaderClass = classLoader.getClass();
         }
      } catch (ClassNotFoundException ex) {
         instrumentableLoaderClass = classLoader.getClass();
      }

      try {
         this.addTransformerMethod = instrumentableLoaderClass.getMethod("addTransformer", ClassFileTransformer.class);
         Method copyMethod = ClassUtils.getMethodIfAvailable(instrumentableLoaderClass, "copyWithoutTransformers", new Class[0]);
         if (copyMethod == null) {
            copyMethod = instrumentableLoaderClass.getMethod("getThrowawayClassLoader");
         }

         this.copyMethod = copyMethod;
      } catch (Throwable ex) {
         throw new IllegalStateException("Could not initialize TomcatLoadTimeWeaver because Tomcat API classes are not available", ex);
      }
   }

   @Override
   public void addTransformer(ClassFileTransformer transformer) {
      try {
         this.addTransformerMethod.invoke(this.classLoader, transformer);
      } catch (InvocationTargetException ex) {
         throw new IllegalStateException("Tomcat addTransformer method threw exception", ex.getCause());
      } catch (Throwable ex) {
         throw new IllegalStateException("Could not invoke Tomcat addTransformer method", ex);
      }
   }

   @Override
   public ClassLoader getInstrumentableClassLoader() {
      return this.classLoader;
   }

   @Override
   public ClassLoader getThrowawayClassLoader() {
      try {
         return new OverridingClassLoader(this.classLoader, (ClassLoader)this.copyMethod.invoke(this.classLoader));
      } catch (InvocationTargetException ex) {
         throw new IllegalStateException("Tomcat copy method threw exception", ex.getCause());
      } catch (Throwable ex) {
         throw new IllegalStateException("Could not invoke Tomcat copy method", ex);
      }
   }
}
