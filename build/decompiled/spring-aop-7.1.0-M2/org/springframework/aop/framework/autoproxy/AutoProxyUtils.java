package org.springframework.aop.framework.autoproxy;

import org.jspecify.annotations.Nullable;
import org.springframework.aop.framework.ProxyConfig;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.Conventions;
import org.springframework.util.StringUtils;

public abstract class AutoProxyUtils {
   public static final String DEFAULT_PROXY_CONFIG_BEAN_NAME = "org.springframework.aop.framework.autoproxy.defaultProxyConfig";
   public static final String EXPOSED_INTERFACES_ATTRIBUTE = Conventions.getQualifiedAttributeName(AutoProxyUtils.class, "exposedInterfaces");
   public static final Object ALL_INTERFACES_ATTRIBUTE_VALUE = new Class[0];
   public static final String PRESERVE_TARGET_CLASS_ATTRIBUTE = Conventions.getQualifiedAttributeName(AutoProxyUtils.class, "preserveTargetClass");
   public static final String ORIGINAL_TARGET_CLASS_ATTRIBUTE = Conventions.getQualifiedAttributeName(AutoProxyUtils.class, "originalTargetClass");

   static void applyDefaultProxyConfig(ProxyConfig proxyConfig, BeanFactory beanFactory) {
      if (beanFactory.containsBean("org.springframework.aop.framework.autoproxy.defaultProxyConfig")) {
         ProxyConfig defaultProxyConfig = (ProxyConfig)beanFactory.getBean("org.springframework.aop.framework.autoproxy.defaultProxyConfig", ProxyConfig.class);
         proxyConfig.copyDefault(defaultProxyConfig);
      }
   }

   static Class<?> @Nullable [] determineExposedInterfaces(ConfigurableListableBeanFactory beanFactory, @Nullable String beanName) {
      if (beanName != null && beanFactory.containsBeanDefinition(beanName)) {
         BeanDefinition bd = beanFactory.getBeanDefinition(beanName);
         Object interfaces = bd.getAttribute(EXPOSED_INTERFACES_ATTRIBUTE);
         if (interfaces instanceof Class<?>[] ifcs) {
            return ifcs;
         }

         if (interfaces instanceof Class<?> ifc) {
            return new Class[]{ifc};
         }
      }

      return null;
   }

   public static boolean shouldProxyTargetClass(ConfigurableListableBeanFactory beanFactory, @Nullable String beanName) {
      if (beanName != null && beanFactory.containsBeanDefinition(beanName)) {
         BeanDefinition bd = beanFactory.getBeanDefinition(beanName);
         return Boolean.TRUE.equals(bd.getAttribute(PRESERVE_TARGET_CLASS_ATTRIBUTE));
      } else {
         return false;
      }
   }

   public static @Nullable Class<?> determineTargetClass(ConfigurableListableBeanFactory beanFactory, @Nullable String beanName) {
      if (beanName == null) {
         return null;
      }

      if (beanFactory.containsBeanDefinition(beanName)) {
         BeanDefinition bd = beanFactory.getMergedBeanDefinition(beanName);
         Class<?> targetClass = (Class<?>)bd.getAttribute(ORIGINAL_TARGET_CLASS_ATTRIBUTE);
         if (targetClass != null) {
            return targetClass;
         }
      }

      return beanFactory.getType(beanName);
   }

   static void exposeTargetClass(ConfigurableListableBeanFactory beanFactory, @Nullable String beanName, Class<?> targetClass) {
      if (beanName != null && beanFactory.containsBeanDefinition(beanName)) {
         beanFactory.getMergedBeanDefinition(beanName).setAttribute(ORIGINAL_TARGET_CLASS_ATTRIBUTE, targetClass);
      }
   }

   static boolean isOriginalInstance(String beanName, Class<?> beanClass) {
      return StringUtils.hasLength(beanName) && beanName.length() == beanClass.getName().length() + ".ORIGINAL".length()
         ? beanName.startsWith(beanClass.getName()) && beanName.endsWith(".ORIGINAL")
         : false;
   }
}
