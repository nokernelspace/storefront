package org.springframework.context.weaving;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import org.aspectj.weaver.loadtime.ClassPreProcessorAgentAdapter;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.instrument.classloading.InstrumentationLoadTimeWeaver;
import org.springframework.instrument.classloading.LoadTimeWeaver;

public class AspectJWeavingEnabler implements BeanFactoryPostProcessor, BeanClassLoaderAware, LoadTimeWeaverAware, Ordered {
   public static final String ASPECTJ_AOP_XML_RESOURCE = "META-INF/aop.xml";
   private @Nullable ClassLoader beanClassLoader;
   private @Nullable LoadTimeWeaver loadTimeWeaver;

   public void setBeanClassLoader(ClassLoader classLoader) {
      this.beanClassLoader = classLoader;
   }

   @Override
   public void setLoadTimeWeaver(LoadTimeWeaver loadTimeWeaver) {
      this.loadTimeWeaver = loadTimeWeaver;
   }

   public int getOrder() {
      return Integer.MIN_VALUE;
   }

   public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
      enableAspectJWeaving(this.loadTimeWeaver, this.beanClassLoader);
   }

   public static void enableAspectJWeaving(@Nullable LoadTimeWeaver weaverToUse, @Nullable ClassLoader beanClassLoader) {
      if (weaverToUse == null) {
         if (!InstrumentationLoadTimeWeaver.isInstrumentationAvailable()) {
            throw new IllegalStateException("No LoadTimeWeaver available");
         }

         weaverToUse = new InstrumentationLoadTimeWeaver(beanClassLoader);
      }

      weaverToUse.addTransformer(new AspectJWeavingEnabler.AspectJClassBypassingClassFileTransformer(new ClassPreProcessorAgentAdapter()));
   }

   private static class AspectJClassBypassingClassFileTransformer implements ClassFileTransformer {
      private final ClassFileTransformer delegate;

      public AspectJClassBypassingClassFileTransformer(ClassFileTransformer delegate) {
         this.delegate = delegate;
      }

      @Override
      public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
         return !className.startsWith("org.aspectj") && !className.startsWith("org/aspectj")
            ? this.delegate.transform(loader, className, classBeingRedefined, protectionDomain, classfileBuffer)
            : classfileBuffer;
      }
   }
}
