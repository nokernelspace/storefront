package org.springframework.aop.config;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.aspectj.annotation.AnnotationAwareAspectJAutoProxyCreator;
import org.springframework.aop.aspectj.autoproxy.AspectJAwareAdvisorAutoProxyCreator;
import org.springframework.aop.framework.ProxyConfig;
import org.springframework.aop.framework.autoproxy.InfrastructureAdvisorAutoProxyCreator;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.util.Assert;

public abstract class AopConfigUtils {
   public static final String AUTO_PROXY_CREATOR_BEAN_NAME = "org.springframework.aop.config.internalAutoProxyCreator";
   private static final List<Class<?>> APC_PRIORITY_LIST = new ArrayList<>(3);

   public static @Nullable BeanDefinition registerAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry) {
      return registerAutoProxyCreatorIfNecessary(registry, null);
   }

   public static @Nullable BeanDefinition registerAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry, @Nullable Object source) {
      return registerOrEscalateApcAsRequired(InfrastructureAdvisorAutoProxyCreator.class, registry, source);
   }

   public static @Nullable BeanDefinition registerAspectJAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry) {
      return registerAspectJAutoProxyCreatorIfNecessary(registry, null);
   }

   public static @Nullable BeanDefinition registerAspectJAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry, @Nullable Object source) {
      return registerOrEscalateApcAsRequired(AspectJAwareAdvisorAutoProxyCreator.class, registry, source);
   }

   public static @Nullable BeanDefinition registerAspectJAnnotationAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry) {
      return registerAspectJAnnotationAutoProxyCreatorIfNecessary(registry, null);
   }

   public static @Nullable BeanDefinition registerAspectJAnnotationAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry, @Nullable Object source) {
      return registerOrEscalateApcAsRequired(AnnotationAwareAspectJAutoProxyCreator.class, registry, source);
   }

   public static void forceAutoProxyCreatorToUseClassProxying(BeanDefinitionRegistry registry) {
      defaultProxyConfig(registry).getPropertyValues().add("proxyTargetClass", Boolean.TRUE);
   }

   public static void forceAutoProxyCreatorToExposeProxy(BeanDefinitionRegistry registry) {
      defaultProxyConfig(registry).getPropertyValues().add("exposeProxy", Boolean.TRUE);
   }

   private static BeanDefinition defaultProxyConfig(BeanDefinitionRegistry registry) {
      if (registry.containsBeanDefinition("org.springframework.aop.framework.autoproxy.defaultProxyConfig")) {
         return registry.getBeanDefinition("org.springframework.aop.framework.autoproxy.defaultProxyConfig");
      }

      RootBeanDefinition beanDefinition = new RootBeanDefinition(ProxyConfig.class);
      beanDefinition.setSource(AopConfigUtils.class);
      beanDefinition.setRole(2);
      registry.registerBeanDefinition("org.springframework.aop.framework.autoproxy.defaultProxyConfig", beanDefinition);
      return beanDefinition;
   }

   private static @Nullable BeanDefinition registerOrEscalateApcAsRequired(Class<?> cls, BeanDefinitionRegistry registry, @Nullable Object source) {
      Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
      if (registry.containsBeanDefinition("org.springframework.aop.config.internalAutoProxyCreator")) {
         BeanDefinition beanDefinition = registry.getBeanDefinition("org.springframework.aop.config.internalAutoProxyCreator");
         if (!cls.getName().equals(beanDefinition.getBeanClassName())) {
            int currentPriority = findPriorityForClass(beanDefinition.getBeanClassName());
            int requiredPriority = findPriorityForClass(cls);
            if (currentPriority < requiredPriority) {
               beanDefinition.setBeanClassName(cls.getName());
            }
         }

         return null;
      } else {
         RootBeanDefinition beanDefinition = new RootBeanDefinition(cls);
         beanDefinition.setSource(source);
         beanDefinition.setRole(2);
         beanDefinition.getPropertyValues().add("order", Integer.MIN_VALUE);
         registry.registerBeanDefinition("org.springframework.aop.config.internalAutoProxyCreator", beanDefinition);
         return beanDefinition;
      }
   }

   private static int findPriorityForClass(Class<?> clazz) {
      return APC_PRIORITY_LIST.indexOf(clazz);
   }

   private static int findPriorityForClass(@Nullable String className) {
      for (int i = 0; i < APC_PRIORITY_LIST.size(); i++) {
         Class<?> clazz = APC_PRIORITY_LIST.get(i);
         if (clazz.getName().equals(className)) {
            return i;
         }
      }

      throw new IllegalArgumentException("Class name [" + className + "] is not a known auto-proxy creator class");
   }

   static {
      APC_PRIORITY_LIST.add(InfrastructureAdvisorAutoProxyCreator.class);
      APC_PRIORITY_LIST.add(AspectJAwareAdvisorAutoProxyCreator.class);
      APC_PRIORITY_LIST.add(AnnotationAwareAspectJAutoProxyCreator.class);
   }
}
