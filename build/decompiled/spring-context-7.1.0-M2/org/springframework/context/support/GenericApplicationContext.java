package org.springframework.context.support;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.support.ClassHintUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionCustomizer;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.BeanRegistryAdapter;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.io.ProtocolResolver;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.util.Assert;

public class GenericApplicationContext extends AbstractApplicationContext implements BeanDefinitionRegistry {
   private static final String DEFERRED_REGISTRY_POST_PROCESSOR_BEAN_NAME = GenericApplicationContext.class.getName() + ".deferredRegistryPostProcessor";
   private final DefaultListableBeanFactory beanFactory;
   private @Nullable ResourceLoader resourceLoader;
   private boolean customClassLoader = false;
   private final AtomicBoolean refreshed = new AtomicBoolean();

   public GenericApplicationContext() {
      this.beanFactory = new DefaultListableBeanFactory();
   }

   public GenericApplicationContext(DefaultListableBeanFactory beanFactory) {
      Assert.notNull(beanFactory, "BeanFactory must not be null");
      this.beanFactory = beanFactory;
   }

   public GenericApplicationContext(@Nullable ApplicationContext parent) {
      this();
      this.setParent(parent);
   }

   public GenericApplicationContext(DefaultListableBeanFactory beanFactory, ApplicationContext parent) {
      this(beanFactory);
      this.setParent(parent);
   }

   @Override
   public void setParent(@Nullable ApplicationContext parent) {
      super.setParent(parent);
      this.beanFactory.setParentBeanFactory(this.getInternalParentBeanFactory());
   }

   @Override
   public void setApplicationStartup(ApplicationStartup applicationStartup) {
      super.setApplicationStartup(applicationStartup);
      this.beanFactory.setApplicationStartup(applicationStartup);
   }

   public void setAllowBeanDefinitionOverriding(boolean allowBeanDefinitionOverriding) {
      this.beanFactory.setAllowBeanDefinitionOverriding(allowBeanDefinitionOverriding);
   }

   public void setAllowCircularReferences(boolean allowCircularReferences) {
      this.beanFactory.setAllowCircularReferences(allowCircularReferences);
   }

   public void setResourceLoader(ResourceLoader resourceLoader) {
      this.resourceLoader = resourceLoader;
   }

   public Resource getResource(String location) {
      if (this.resourceLoader != null) {
         for (ProtocolResolver protocolResolver : this.getProtocolResolvers()) {
            Resource resource = protocolResolver.resolve(location, this);
            if (resource != null) {
               return resource;
            }
         }

         return this.resourceLoader.getResource(location);
      } else {
         return super.getResource(location);
      }
   }

   @Override
   public Resource[] getResources(String locationPattern) throws IOException {
      return this.resourceLoader instanceof ResourcePatternResolver resourcePatternResolver
         ? resourcePatternResolver.getResources(locationPattern)
         : super.getResources(locationPattern);
   }

   @Override
   public void setClassLoader(@Nullable ClassLoader classLoader) {
      super.setClassLoader(classLoader);
      this.customClassLoader = true;
   }

   public @Nullable ClassLoader getClassLoader() {
      return this.resourceLoader != null && !this.customClassLoader ? this.resourceLoader.getClassLoader() : super.getClassLoader();
   }

   @Override
   protected final void refreshBeanFactory() throws IllegalStateException {
      if (!this.refreshed.compareAndSet(false, true)) {
         throw new IllegalStateException("GenericApplicationContext does not support multiple refresh attempts: just call 'refresh' once");
      }

      this.beanFactory.setSerializationId(this.getId());
   }

   @Override
   protected void cancelRefresh(Throwable ex) {
      this.beanFactory.setSerializationId(null);
      super.cancelRefresh(ex);
   }

   @Override
   protected final void closeBeanFactory() {
      this.beanFactory.setSerializationId(null);
   }

   @Override
   public final ConfigurableListableBeanFactory getBeanFactory() {
      return this.beanFactory;
   }

   public final DefaultListableBeanFactory getDefaultListableBeanFactory() {
      return this.beanFactory;
   }

   @Override
   public AutowireCapableBeanFactory getAutowireCapableBeanFactory() throws IllegalStateException {
      this.assertBeanFactoryActive();
      return this.beanFactory;
   }

   public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition) throws BeanDefinitionStoreException {
      this.beanFactory.registerBeanDefinition(beanName, beanDefinition);
   }

   public void removeBeanDefinition(String beanName) throws NoSuchBeanDefinitionException {
      this.beanFactory.removeBeanDefinition(beanName);
   }

   public BeanDefinition getBeanDefinition(String beanName) throws NoSuchBeanDefinitionException {
      return this.beanFactory.getBeanDefinition(beanName);
   }

   public boolean isBeanDefinitionOverridable(String beanName) {
      return this.beanFactory.isBeanDefinitionOverridable(beanName);
   }

   public boolean isBeanNameInUse(String beanName) {
      return this.beanFactory.isBeanNameInUse(beanName);
   }

   public void registerAlias(String beanName, String alias) {
      this.beanFactory.registerAlias(beanName, alias);
   }

   public void removeAlias(String alias) {
      this.beanFactory.removeAlias(alias);
   }

   public boolean isAlias(String beanName) {
      return this.beanFactory.isAlias(beanName);
   }

   public void refreshForAotProcessing(RuntimeHints runtimeHints) {
      if (this.logger.isDebugEnabled()) {
         this.logger.debug("Preparing bean factory for AOT processing");
      }

      this.prepareRefresh();
      this.obtainFreshBeanFactory();
      this.prepareBeanFactory(this.beanFactory);
      this.postProcessBeanFactory(this.beanFactory);
      this.invokeBeanFactoryPostProcessors(this.beanFactory);
      this.beanFactory.freezeConfiguration();
      PostProcessorRegistrationDelegate.invokeMergedBeanDefinitionPostProcessors(this.beanFactory);
      this.preDetermineBeanTypes(runtimeHints);
   }

   private void preDetermineBeanTypes(RuntimeHints runtimeHints) {
      List<String> singletons = new ArrayList<>();
      List<String> lazyBeans = new ArrayList<>();

      for (String beanName : this.beanFactory.getSingletonNames()) {
         Class<?> beanType = this.beanFactory.getType(beanName);
         if (beanType != null) {
            ClassHintUtils.registerProxyIfNecessary(beanType, runtimeHints);
         }

         singletons.add(beanName);
      }

      List<SmartInstantiationAwareBeanPostProcessor> bpps = PostProcessorRegistrationDelegate.loadBeanPostProcessors(
         this.beanFactory, SmartInstantiationAwareBeanPostProcessor.class
      );

      for (String beanName : this.beanFactory.getBeanDefinitionNames()) {
         if (!singletons.contains(beanName)) {
            BeanDefinition bd = this.getBeanDefinition(beanName);
            if (bd.isSingleton() && !bd.isLazyInit()) {
               this.preDetermineBeanType(beanName, bpps, runtimeHints);
            } else {
               lazyBeans.add(beanName);
            }
         }
      }

      for (String beanName : lazyBeans) {
         this.preDetermineBeanType(beanName, bpps, runtimeHints);
      }
   }

   private void preDetermineBeanType(String beanName, List<SmartInstantiationAwareBeanPostProcessor> bpps, RuntimeHints runtimeHints) {
      Class<?> beanType = this.beanFactory.getType(beanName);
      if (beanType != null) {
         ClassHintUtils.registerProxyIfNecessary(beanType, runtimeHints);

         for (SmartInstantiationAwareBeanPostProcessor bpp : bpps) {
            Class<?> newBeanType = bpp.determineBeanType(beanType, beanName);
            if (newBeanType != beanType) {
               ClassHintUtils.registerProxyIfNecessary(newBeanType, runtimeHints);
               beanType = newBeanType;
            }
         }
      }
   }

   public <T> void registerBean(Class<T> beanClass, @Nullable Object... constructorArgs) {
      this.registerBean(null, beanClass, constructorArgs);
   }

   public <T> void registerBean(@Nullable String beanName, Class<T> beanClass, @Nullable Object... constructorArgs) {
      this.registerBean(beanName, beanClass, (Supplier<T>)null, bd -> {
         for (Object arg : constructorArgs) {
            bd.getConstructorArgumentValues().addGenericArgumentValue(arg);
         }
      });
   }

   public final <T> void registerBean(Class<T> beanClass, BeanDefinitionCustomizer... customizers) {
      this.registerBean(null, beanClass, null, customizers);
   }

   public final <T> void registerBean(@Nullable String beanName, Class<T> beanClass, BeanDefinitionCustomizer... customizers) {
      this.registerBean(beanName, beanClass, null, customizers);
   }

   public final <T> void registerBean(Class<T> beanClass, Supplier<T> supplier, BeanDefinitionCustomizer... customizers) {
      this.registerBean(null, beanClass, supplier, customizers);
   }

   public <T> void registerBean(@Nullable String beanName, Class<T> beanClass, @Nullable Supplier<T> supplier, BeanDefinitionCustomizer... customizers) {
      GenericApplicationContext.ClassDerivedBeanDefinition beanDefinition = new GenericApplicationContext.ClassDerivedBeanDefinition(beanClass);
      if (supplier != null) {
         beanDefinition.setInstanceSupplier(supplier);
      }

      for (BeanDefinitionCustomizer customizer : customizers) {
         customizer.customize(beanDefinition);
      }

      String nameToUse = beanName != null ? beanName : beanClass.getName();
      this.registerBeanDefinition(nameToUse, beanDefinition);
   }

   public void register(BeanRegistrar... registrars) {
      for (BeanRegistrar registrar : registrars) {
         GenericApplicationContext.DeferredRegistryPostProcessor pp = (GenericApplicationContext.DeferredRegistryPostProcessor)this.beanFactory
            .getSingleton(DEFERRED_REGISTRY_POST_PROCESSOR_BEAN_NAME);
         if (pp == null) {
            pp = new GenericApplicationContext.DeferredRegistryPostProcessor();
            this.beanFactory.registerSingleton(DEFERRED_REGISTRY_POST_PROCESSOR_BEAN_NAME, pp);
         }

         pp.addRegistrar(registrar);
      }
   }

   private static class ClassDerivedBeanDefinition extends RootBeanDefinition {
      public ClassDerivedBeanDefinition(Class<?> beanClass) {
         super(beanClass);
      }

      public ClassDerivedBeanDefinition(GenericApplicationContext.ClassDerivedBeanDefinition original) {
         super(original);
      }

      public Constructor<?> @Nullable [] getPreferredConstructors() {
         Constructor<?>[] fromAttribute = super.getPreferredConstructors();
         if (fromAttribute != null) {
            return fromAttribute;
         }

         Class<?> clazz = this.getBeanClass();
         Constructor<?> primaryCtor = BeanUtils.findPrimaryConstructor(clazz);
         if (primaryCtor != null) {
            return new Constructor[]{primaryCtor};
         }

         Constructor<?>[] publicCtors = clazz.getConstructors();
         return publicCtors.length > 0 ? publicCtors : null;
      }

      public RootBeanDefinition cloneBeanDefinition() {
         return new GenericApplicationContext.ClassDerivedBeanDefinition(this);
      }
   }

   private class DeferredRegistryPostProcessor implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {
      private final List<BeanRegistrar> registrars = new ArrayList<>();

      public void addRegistrar(BeanRegistrar registrar) {
         this.registrars.add(registrar);
      }

      public int getOrder() {
         return 2147483646;
      }

      public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
         for (BeanRegistrar registrar : this.registrars) {
            new BeanRegistryAdapter(GenericApplicationContext.this.beanFactory, GenericApplicationContext.this.getEnvironment(), registrar.getClass())
               .register(registrar);
         }
      }
   }
}
