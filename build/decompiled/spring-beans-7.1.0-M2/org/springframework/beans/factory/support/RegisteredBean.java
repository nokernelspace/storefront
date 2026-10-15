package org.springframework.beans.factory.support;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.core.ResolvableType;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

public final class RegisteredBean {
   private final ConfigurableListableBeanFactory beanFactory;
   private final Supplier<String> beanName;
   private final boolean generatedBeanName;
   private final Supplier<RootBeanDefinition> mergedBeanDefinition;
   private final @Nullable RegisteredBean parent;

   private RegisteredBean(
      ConfigurableListableBeanFactory beanFactory,
      Supplier<String> beanName,
      boolean generatedBeanName,
      Supplier<RootBeanDefinition> mergedBeanDefinition,
      @Nullable RegisteredBean parent
   ) {
      this.beanFactory = beanFactory;
      this.beanName = beanName;
      this.generatedBeanName = generatedBeanName;
      this.mergedBeanDefinition = mergedBeanDefinition;
      this.parent = parent;
   }

   public static RegisteredBean of(ConfigurableListableBeanFactory beanFactory, String beanName) {
      Assert.notNull(beanFactory, "'beanFactory' must not be null");
      Assert.hasLength(beanName, "'beanName' must not be empty");
      return new RegisteredBean(beanFactory, () -> beanName, false, () -> (RootBeanDefinition)beanFactory.getMergedBeanDefinition(beanName), null);
   }

   static RegisteredBean of(ConfigurableListableBeanFactory beanFactory, String beanName, RootBeanDefinition mbd) {
      return new RegisteredBean(beanFactory, () -> beanName, false, () -> mbd, null);
   }

   public static RegisteredBean ofInnerBean(RegisteredBean parent, BeanDefinitionHolder innerBean) {
      Assert.notNull(innerBean, "'innerBean' must not be null");
      return ofInnerBean(parent, innerBean.getBeanName(), innerBean.getBeanDefinition());
   }

   public static RegisteredBean ofInnerBean(RegisteredBean parent, BeanDefinition innerBeanDefinition) {
      return ofInnerBean(parent, null, innerBeanDefinition);
   }

   public static RegisteredBean ofInnerBean(RegisteredBean parent, @Nullable String innerBeanName, BeanDefinition innerBeanDefinition) {
      Assert.notNull(parent, "'parent' must not be null");
      Assert.notNull(innerBeanDefinition, "'innerBeanDefinition' must not be null");
      RegisteredBean.InnerBeanResolver resolver = new RegisteredBean.InnerBeanResolver(parent, innerBeanName, innerBeanDefinition);
      Supplier<String> beanName = StringUtils.hasLength(innerBeanName) ? () -> innerBeanName : resolver::resolveBeanName;
      return new RegisteredBean(parent.getBeanFactory(), beanName, innerBeanName == null, resolver::resolveMergedBeanDefinition, parent);
   }

   public String getBeanName() {
      return this.beanName.get();
   }

   public boolean isGeneratedBeanName() {
      return this.generatedBeanName;
   }

   public ConfigurableListableBeanFactory getBeanFactory() {
      return this.beanFactory;
   }

   public Class<?> getBeanClass() {
      return ClassUtils.getUserClass(this.getBeanType().toClass());
   }

   public ResolvableType getBeanType() {
      return this.getMergedBeanDefinition().getResolvableType();
   }

   public RootBeanDefinition getMergedBeanDefinition() {
      return this.mergedBeanDefinition.get();
   }

   public boolean isInnerBean() {
      return this.parent != null;
   }

   public @Nullable RegisteredBean getParent() {
      return this.parent;
   }

   @Deprecated(since = "6.1.7")
   public Executable resolveConstructorOrFactoryMethod() {
      return new ConstructorResolver((AbstractAutowireCapableBeanFactory)this.getBeanFactory())
         .resolveConstructorOrFactoryMethod(this.getBeanName(), this.getMergedBeanDefinition());
   }

   public RegisteredBean.InstantiationDescriptor resolveInstantiationDescriptor() {
      Executable executable = this.resolveConstructorOrFactoryMethod();
      if (executable instanceof Method method && !Modifier.isStatic(method.getModifiers())) {
         String factoryBeanName = this.getMergedBeanDefinition().getFactoryBeanName();
         if (factoryBeanName != null && this.beanFactory.containsBean(factoryBeanName)) {
            return new RegisteredBean.InstantiationDescriptor(
               executable, this.beanFactory.getMergedBeanDefinition(factoryBeanName).getResolvableType().toClass()
            );
         }
      }

      return new RegisteredBean.InstantiationDescriptor(executable, executable.getDeclaringClass());
   }

   public @Nullable Object resolveAutowiredArgument(DependencyDescriptor descriptor, TypeConverter typeConverter, Set<String> autowiredBeanNames) {
      return new ConstructorResolver((AbstractAutowireCapableBeanFactory)this.getBeanFactory())
         .resolveAutowiredArgument(descriptor, descriptor.getDependencyType(), this.getBeanName(), autowiredBeanNames, typeConverter, true);
   }

   @Override
   public String toString() {
      return new ToStringCreator(this).append("beanName", this.getBeanName()).append("mergedBeanDefinition", this.getMergedBeanDefinition()).toString();
   }

   private static class InnerBeanResolver {
      private final RegisteredBean parent;
      private final @Nullable String innerBeanName;
      private final BeanDefinition innerBeanDefinition;
      private volatile @Nullable String resolvedBeanName;

      InnerBeanResolver(RegisteredBean parent, @Nullable String innerBeanName, BeanDefinition innerBeanDefinition) {
         Assert.isInstanceOf(AbstractAutowireCapableBeanFactory.class, parent.getBeanFactory());
         this.parent = parent;
         this.innerBeanName = innerBeanName;
         this.innerBeanDefinition = innerBeanDefinition;
      }

      String resolveBeanName() {
         String resolvedBeanName = this.resolvedBeanName;
         if (resolvedBeanName != null) {
            return resolvedBeanName;
         }

         resolvedBeanName = this.resolveInnerBean((beanName, mergedBeanDefinition) -> beanName);
         this.resolvedBeanName = resolvedBeanName;
         return resolvedBeanName;
      }

      RootBeanDefinition resolveMergedBeanDefinition() {
         return this.resolveInnerBean((beanName, mergedBeanDefinition) -> mergedBeanDefinition);
      }

      private <T> T resolveInnerBean(BiFunction<String, RootBeanDefinition, T> resolver) {
         BeanDefinitionValueResolver beanDefinitionValueResolver = new BeanDefinitionValueResolver(
            (AbstractAutowireCapableBeanFactory)this.parent.getBeanFactory(), this.parent.getBeanName(), this.parent.getMergedBeanDefinition()
         );
         return beanDefinitionValueResolver.resolveInnerBean(this.innerBeanName, this.innerBeanDefinition, resolver);
      }
   }

   public record InstantiationDescriptor(Executable executable, Class<?> targetClass) {
      public InstantiationDescriptor(Executable executable) {
         this(executable, executable.getDeclaringClass());
      }
   }
}
