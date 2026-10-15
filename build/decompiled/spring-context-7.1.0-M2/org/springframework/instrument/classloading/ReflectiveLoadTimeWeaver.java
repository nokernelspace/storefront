package org.springframework.instrument.classloading;

import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.Method;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.DecoratingClassLoader;
import org.springframework.core.OverridingClassLoader;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

public class ReflectiveLoadTimeWeaver implements LoadTimeWeaver {
   private static final String ADD_TRANSFORMER_METHOD_NAME = "addTransformer";
   private static final String GET_THROWAWAY_CLASS_LOADER_METHOD_NAME = "getThrowawayClassLoader";
   private static final Log logger = LogFactory.getLog(ReflectiveLoadTimeWeaver.class);
   private final ClassLoader classLoader;
   private final Method addTransformerMethod;
   private final @Nullable Method getThrowawayClassLoaderMethod;

   public ReflectiveLoadTimeWeaver() {
      this(ClassUtils.getDefaultClassLoader());
   }

   public ReflectiveLoadTimeWeaver(@Nullable ClassLoader classLoader) {
      Assert.notNull(classLoader, "ClassLoader must not be null");
      this.classLoader = classLoader;
      Method addTransformerMethod = ClassUtils.getMethodIfAvailable(this.classLoader.getClass(), "addTransformer", new Class[]{ClassFileTransformer.class});
      if (addTransformerMethod == null) {
         throw new IllegalStateException(
            "ClassLoader [" + classLoader.getClass().getName() + "] does NOT provide an 'addTransformer(ClassFileTransformer)' method."
         );
      }

      this.addTransformerMethod = addTransformerMethod;
      Method getThrowawayClassLoaderMethod = ClassUtils.getMethodIfAvailable(this.classLoader.getClass(), "getThrowawayClassLoader", new Class[0]);
      if (getThrowawayClassLoaderMethod == null && logger.isDebugEnabled()) {
         logger.debug(
            "The ClassLoader ["
               + classLoader.getClass().getName()
               + "] does NOT provide a 'getThrowawayClassLoader()' method; SimpleThrowawayClassLoader will be used instead."
         );
      }

      this.getThrowawayClassLoaderMethod = getThrowawayClassLoaderMethod;
   }

   @Override
   public void addTransformer(ClassFileTransformer transformer) {
      Assert.notNull(transformer, "Transformer must not be null");
      ReflectionUtils.invokeMethod(this.addTransformerMethod, this.classLoader, new Object[]{transformer});
   }

   @Override
   public ClassLoader getInstrumentableClassLoader() {
      return this.classLoader;
   }

   @Override
   public ClassLoader getThrowawayClassLoader() {
      if (this.getThrowawayClassLoaderMethod != null) {
         ClassLoader target = (ClassLoader)ReflectionUtils.invokeMethod(this.getThrowawayClassLoaderMethod, this.classLoader);
         return (ClassLoader)(target instanceof DecoratingClassLoader ? target : new OverridingClassLoader(this.classLoader, target));
      } else {
         return new SimpleThrowawayClassLoader(this.classLoader);
      }
   }
}
