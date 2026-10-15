package org.springframework.beans.factory.support;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.beans.factory.BeanRegistry;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinitionCustomizer;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;

public class BeanRegistryAdapter implements BeanRegistry {
   private final BeanDefinitionRegistry beanRegistry;
   private final ListableBeanFactory beanFactory;
   private final Environment environment;
   private final Class<? extends BeanRegistrar> beanRegistrarClass;
   private final @Nullable MultiValueMap<String, BeanDefinitionCustomizer> customizers;

   public BeanRegistryAdapter(DefaultListableBeanFactory beanFactory, Environment environment, Class<? extends BeanRegistrar> beanRegistrarClass) {
      this(beanFactory, beanFactory, environment, beanRegistrarClass, null);
   }

   public BeanRegistryAdapter(
      BeanDefinitionRegistry beanRegistry, ListableBeanFactory beanFactory, Environment environment, Class<? extends BeanRegistrar> beanRegistrarClass
   ) {
      this(beanRegistry, beanFactory, environment, beanRegistrarClass, null);
   }

   public BeanRegistryAdapter(
      BeanDefinitionRegistry beanRegistry,
      ListableBeanFactory beanFactory,
      Environment environment,
      Class<? extends BeanRegistrar> beanRegistrarClass,
      @Nullable MultiValueMap<String, BeanDefinitionCustomizer> customizers
   ) {
      this.beanRegistry = beanRegistry;
      this.beanFactory = beanFactory;
      this.environment = environment;
      this.beanRegistrarClass = beanRegistrarClass;
      this.customizers = customizers;
   }

   @Override
   public void register(BeanRegistrar registrar) {
      Assert.notNull(registrar, "BeanRegistrar must not be null");
      registrar.register(this, this.environment);
   }

   @Override
   public void registerAlias(String name, String alias) {
      this.beanRegistry.registerAlias(name, alias);
   }

   @Override
   public <T> String registerBean(Class<T> beanClass) {
      String beanName = BeanDefinitionReaderUtils.uniqueBeanName(beanClass.getName(), this.beanRegistry);
      this.registerBean(beanName, beanClass);
      return beanName;
   }

   @Override
   public <T> String registerBean(ParameterizedTypeReference<T> beanType) {
      ResolvableType resolvableType = ResolvableType.forType(beanType);
      String beanName = BeanDefinitionReaderUtils.uniqueBeanName(Objects.requireNonNull(resolvableType.resolve()).getName(), this.beanRegistry);
      this.registerBean(beanName, beanType);
      return beanName;
   }

   @Override
   public <T> String registerBean(Class<T> beanClass, Consumer<BeanRegistry.Spec<T>> customizer) {
      String beanName = BeanDefinitionReaderUtils.uniqueBeanName(beanClass.getName(), this.beanRegistry);
      this.registerBean(beanName, beanClass, customizer);
      return beanName;
   }

   @Override
   public <T> String registerBean(ParameterizedTypeReference<T> beanType, Consumer<BeanRegistry.Spec<T>> customizer) {
      ResolvableType resolvableType = ResolvableType.forType(beanType);
      Class<?> beanClass = Objects.requireNonNull(resolvableType.resolve());
      String beanName = BeanDefinitionReaderUtils.uniqueBeanName(beanClass.getName(), this.beanRegistry);
      this.registerBean(beanName, beanType, customizer);
      return beanName;
   }

   @Override
   public <T> void registerBean(String name, Class<T> beanClass) {
      BeanRegistryAdapter.BeanRegistrarBeanDefinition beanDefinition = new BeanRegistryAdapter.BeanRegistrarBeanDefinition(beanClass, this.beanRegistrarClass);
      if (this.customizers != null && this.customizers.containsKey(name)) {
         for (BeanDefinitionCustomizer customizer : (List)this.customizers.get(name)) {
            customizer.customize(beanDefinition);
         }
      }

      this.beanRegistry.registerBeanDefinition(name, beanDefinition);
   }

   @Override
   public <T> void registerBean(String name, ParameterizedTypeReference<T> beanType) {
      ResolvableType resolvableType = ResolvableType.forType(beanType);
      Class<?> beanClass = Objects.requireNonNull(resolvableType.resolve());
      BeanRegistryAdapter.BeanRegistrarBeanDefinition beanDefinition = new BeanRegistryAdapter.BeanRegistrarBeanDefinition(beanClass, this.beanRegistrarClass);
      beanDefinition.setTargetType(resolvableType);
      if (this.customizers != null && this.customizers.containsKey(name)) {
         for (BeanDefinitionCustomizer customizer : (List)this.customizers.get(name)) {
            customizer.customize(beanDefinition);
         }
      }

      this.beanRegistry.registerBeanDefinition(name, beanDefinition);
   }

   @Override
   public <T> void registerBean(String name, Class<T> beanClass, Consumer<BeanRegistry.Spec<T>> customizer) {
      BeanRegistryAdapter.BeanRegistrarBeanDefinition beanDefinition = new BeanRegistryAdapter.BeanRegistrarBeanDefinition(beanClass, this.beanRegistrarClass);
      customizer.accept(new BeanRegistryAdapter.BeanSpecAdapter<>(beanDefinition, this.beanFactory));
      if (this.customizers != null && this.customizers.containsKey(name)) {
         for (BeanDefinitionCustomizer registryCustomizer : (List)this.customizers.get(name)) {
            registryCustomizer.customize(beanDefinition);
         }
      }

      this.beanRegistry.registerBeanDefinition(name, beanDefinition);
   }

   @Override
   public <T> void registerBean(String name, ParameterizedTypeReference<T> beanType, Consumer<BeanRegistry.Spec<T>> customizer) {
      ResolvableType resolvableType = ResolvableType.forType(beanType);
      Class<?> beanClass = Objects.requireNonNull(resolvableType.resolve());
      BeanRegistryAdapter.BeanRegistrarBeanDefinition beanDefinition = new BeanRegistryAdapter.BeanRegistrarBeanDefinition(beanClass, this.beanRegistrarClass);
      beanDefinition.setTargetType(resolvableType);
      customizer.accept(new BeanRegistryAdapter.BeanSpecAdapter<>(beanDefinition, this.beanFactory));
      if (this.customizers != null && this.customizers.containsKey(name)) {
         for (BeanDefinitionCustomizer registryCustomizer : (List)this.customizers.get(name)) {
            registryCustomizer.customize(beanDefinition);
         }
      }

      this.beanRegistry.registerBeanDefinition(name, beanDefinition);
   }

   @Override
   public boolean containsBean(String name) {
      return this.beanFactory.containsBean(name);
   }

   @Override
   public boolean containsBean(Class<?> beanType) {
      return BeanFactoryUtils.beanNamesForTypeIncludingAncestors(this.beanFactory, beanType).length > 0;
   }

   @Override
   public <T> boolean containsBean(ParameterizedTypeReference<T> beanType) {
      ResolvableType resolvableType = ResolvableType.forType(beanType);
      return BeanFactoryUtils.beanNamesForTypeIncludingAncestors(this.beanFactory, resolvableType).length > 0;
   }

   private static class BeanRegistrarBeanDefinition extends RootBeanDefinition {
      public BeanRegistrarBeanDefinition(Class<?> beanClass, Class<? extends BeanRegistrar> beanRegistrarClass) {
         super(beanClass);
         this.setSource(beanRegistrarClass);
         this.setAttribute("aotProcessingIgnoreRegistration", true);
      }

      public BeanRegistrarBeanDefinition(BeanRegistryAdapter.BeanRegistrarBeanDefinition original) {
         super(original);
      }

      @Override
      public Constructor<?> @Nullable [] getPreferredConstructors() {
         if (this.getInstanceSupplier() != null) {
            return null;
         }

         try {
            return new Constructor[]{BeanUtils.getResolvableConstructor(this.getBeanClass())};
         } catch (IllegalStateException ex) {
            return null;
         }
      }

      @Override
      public RootBeanDefinition cloneBeanDefinition() {
         return new BeanRegistryAdapter.BeanRegistrarBeanDefinition(this);
      }
   }

   private static class BeanSpecAdapter<T> implements BeanRegistry.Spec<T> {
      private final RootBeanDefinition beanDefinition;
      private final BeanFactory beanFactory;

      public BeanSpecAdapter(RootBeanDefinition beanDefinition, BeanFactory beanFactory) {
         this.beanDefinition = beanDefinition;
         this.beanFactory = beanFactory;
      }

      @Override
      public BeanRegistry.Spec<T> backgroundInit() {
         this.beanDefinition.setBackgroundInit(true);
         return this;
      }

      @Override
      public BeanRegistry.Spec<T> fallback() {
         this.beanDefinition.setFallback(true);
         return this;
      }

      @Override
      public BeanRegistry.Spec<T> primary() {
         this.beanDefinition.setPrimary(true);
         return this;
      }

      @Override
      public BeanRegistry.Spec<T> description(String description) {
         this.beanDefinition.setDescription(description);
         return this;
      }

      @Override
      public BeanRegistry.Spec<T> infrastructure() {
         this.beanDefinition.setRole(2);
         return this;
      }

      @Override
      public BeanRegistry.Spec<T> lazyInit() {
         this.beanDefinition.setLazyInit(true);
         return this;
      }

      @Override
      public BeanRegistry.Spec<T> notAutowirable() {
         this.beanDefinition.setAutowireCandidate(false);
         return this;
      }

      @Override
      public BeanRegistry.Spec<T> order(int order) {
         this.beanDefinition.setAttribute("order", order);
         return this;
      }

      @Override
      public BeanRegistry.Spec<T> prototype() {
         this.beanDefinition.setScope("prototype");
         return this;
      }

      @Override
      public BeanRegistry.Spec<T> scope(String scope) {
         this.beanDefinition.setScope(scope);
         return this;
      }

      @Override
      public BeanRegistry.Spec<T> supplier(Function<BeanRegistry.SupplierContext, T> supplier) {
         this.beanDefinition.setInstanceSupplier(() -> supplier.apply(new BeanRegistryAdapter.SupplierContextAdapter(this.beanFactory)));
         return this;
      }
   }

   private static class SupplierContextAdapter implements BeanRegistry.SupplierContext {
      private final BeanFactory beanFactory;

      public SupplierContextAdapter(BeanFactory beanFactory) {
         this.beanFactory = beanFactory;
      }

      @Override
      public <T> T bean(Class<T> beanClass) throws BeansException {
         return this.beanFactory.getBean(beanClass);
      }

      @Override
      public <T> T bean(ParameterizedTypeReference<T> beanType) throws BeansException {
         return this.beanFactory.<T>getBeanProvider(beanType).getObject();
      }

      @Override
      public <T> T bean(String name, Class<T> beanClass) throws BeansException {
         return this.beanFactory.getBean(name, beanClass);
      }

      @Override
      public <T> ObjectProvider<T> beanProvider(Class<T> beanClass) {
         return this.beanFactory.getBeanProvider(beanClass);
      }

      @Override
      public <T> ObjectProvider<T> beanProvider(ParameterizedTypeReference<T> beanType) {
         return this.beanFactory.getBeanProvider(beanType);
      }
   }
}
