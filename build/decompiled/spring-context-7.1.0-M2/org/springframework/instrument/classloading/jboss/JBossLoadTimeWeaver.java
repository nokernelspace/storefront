package org.springframework.instrument.classloading.jboss;

import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;
import org.springframework.instrument.classloading.LoadTimeWeaver;
import org.springframework.instrument.classloading.SimpleThrowawayClassLoader;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.function.ThrowingFunction;

public class JBossLoadTimeWeaver implements LoadTimeWeaver {
   private static final String LEGACY_DELEGATING_TRANSFORMER_CLASS_NAME = "org.jboss.as.server.deployment.module.DelegatingClassFileTransformer";
   private static final String DELEGATING_TRANSFORMER_CLASS_NAME = "org.jboss.as.server.deployment.module.DelegatingClassTransformer";
   private static final String CLASS_TRANSFORMER_CLASS_NAME = "org.jboss.modules.ClassTransformer";
   private static final String WRAPPER_TRANSFORMER_CLASS_NAME = "org.jboss.modules.JLIClassTransformer";
   private final ClassLoader classLoader;
   private final Object delegatingTransformer;
   private final Method addTransformer;
   private final ThrowingFunction<Object, Object> adaptTransformer;

   public JBossLoadTimeWeaver() {
      this(ClassUtils.getDefaultClassLoader());
   }

   public JBossLoadTimeWeaver(@Nullable ClassLoader classLoader) {
      Assert.notNull(classLoader, "ClassLoader must not be null");
      this.classLoader = classLoader;

      try {
         Field transformer = ReflectionUtils.findField(classLoader.getClass(), "transformer");
         if (transformer == null) {
            throw new IllegalArgumentException("Could not find 'transformer' field on JBoss ClassLoader: " + classLoader.getClass().getName());
         }

         transformer.setAccessible(true);
         Object suggestedTransformer = transformer.get(classLoader);
         if (suggestedTransformer.getClass().getName().equals("org.jboss.modules.JLIClassTransformer")) {
            Field wrappedTransformer = ReflectionUtils.findField(suggestedTransformer.getClass(), "transformer");
            if (wrappedTransformer == null) {
               throw new IllegalArgumentException(
                  "Could not find 'transformer' field on JBoss JLIClassTransformer: " + suggestedTransformer.getClass().getName()
               );
            }

            wrappedTransformer.setAccessible(true);
            suggestedTransformer = wrappedTransformer.get(suggestedTransformer);
         }

         Class<?> transformerType = ClassFileTransformer.class;
         if (suggestedTransformer.getClass().getName().equals("org.jboss.as.server.deployment.module.DelegatingClassFileTransformer")) {
            this.adaptTransformer = t -> t;
         } else {
            if (!suggestedTransformer.getClass().getName().equals("org.jboss.as.server.deployment.module.DelegatingClassTransformer")) {
               throw new IllegalStateException(
                  "Transformer not of expected type DelegatingClass(File)Transformer: " + suggestedTransformer.getClass().getName()
               );
            }

            transformerType = classLoader.loadClass("org.jboss.modules.ClassTransformer");
            Constructor<?> adaptedTransformer = classLoader.loadClass("org.jboss.modules.JLIClassTransformer").getConstructor(ClassFileTransformer.class);
            Constructor var6 = adaptedTransformer;
            this.adaptTransformer = xva$0 -> var6.newInstance(xva$0);
         }

         this.delegatingTransformer = suggestedTransformer;
         Method addTransformer = ReflectionUtils.findMethod(this.delegatingTransformer.getClass(), "addTransformer", new Class[]{transformerType});
         if (addTransformer == null) {
            throw new IllegalArgumentException(
               "Could not find 'addTransformer' method on JBoss DelegatingClass(File)Transformer: " + this.delegatingTransformer.getClass().getName()
            );
         }

         addTransformer.setAccessible(true);
         this.addTransformer = addTransformer;
      } catch (Throwable ex) {
         throw new IllegalStateException("Could not initialize JBoss LoadTimeWeaver", ex);
      }
   }

   @Override
   public void addTransformer(ClassFileTransformer transformer) {
      try {
         this.addTransformer.invoke(this.delegatingTransformer, this.adaptTransformer.apply(transformer));
      } catch (Throwable ex) {
         throw new IllegalStateException("Could not add transformer on JBoss ClassLoader: " + this.classLoader, ex);
      }
   }

   @Override
   public ClassLoader getInstrumentableClassLoader() {
      return this.classLoader;
   }

   @Override
   public ClassLoader getThrowawayClassLoader() {
      return new SimpleThrowawayClassLoader(this.getInstrumentableClassLoader());
   }
}
