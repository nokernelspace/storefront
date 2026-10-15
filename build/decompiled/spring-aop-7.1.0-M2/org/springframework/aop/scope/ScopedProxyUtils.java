package org.springframework.aop.scope;

import org.jspecify.annotations.Nullable;
import org.springframework.aop.framework.autoproxy.AutoProxyUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.lang.Contract;
import org.springframework.util.Assert;

public abstract class ScopedProxyUtils {
   private static final String TARGET_NAME_PREFIX = "scopedTarget.";
   private static final int TARGET_NAME_PREFIX_LENGTH = "scopedTarget.".length();

   public static BeanDefinitionHolder createScopedProxy(BeanDefinitionHolder definition, BeanDefinitionRegistry registry, boolean proxyTargetClass) {
      String originalBeanName = definition.getBeanName();
      BeanDefinition targetDefinition = definition.getBeanDefinition();
      String targetBeanName = getTargetBeanName(originalBeanName);
      RootBeanDefinition proxyDefinition = new RootBeanDefinition(ScopedProxyFactoryBean.class);
      proxyDefinition.setDecoratedDefinition(new BeanDefinitionHolder(targetDefinition, targetBeanName));
      proxyDefinition.setOriginatingBeanDefinition(targetDefinition);
      proxyDefinition.setSource(definition.getSource());
      proxyDefinition.setRole(targetDefinition.getRole());
      proxyDefinition.getPropertyValues().add("targetBeanName", targetBeanName);
      if (proxyTargetClass) {
         targetDefinition.setAttribute(AutoProxyUtils.PRESERVE_TARGET_CLASS_ATTRIBUTE, Boolean.TRUE);
      } else {
         proxyDefinition.getPropertyValues().add("proxyTargetClass", Boolean.FALSE);
      }

      proxyDefinition.setAutowireCandidate(targetDefinition.isAutowireCandidate());
      proxyDefinition.setPrimary(targetDefinition.isPrimary());
      proxyDefinition.setFallback(targetDefinition.isFallback());
      if (targetDefinition instanceof AbstractBeanDefinition abd) {
         proxyDefinition.setDefaultCandidate(abd.isDefaultCandidate());
         proxyDefinition.copyQualifiersFrom(abd);
      }

      targetDefinition.setAutowireCandidate(false);
      targetDefinition.setPrimary(false);
      targetDefinition.setFallback(false);
      if (targetDefinition instanceof AbstractBeanDefinition abd) {
         abd.setDefaultCandidate(false);
      }

      registry.registerBeanDefinition(targetBeanName, targetDefinition);
      return new BeanDefinitionHolder(proxyDefinition, originalBeanName, definition.getAliases());
   }

   public static String getTargetBeanName(String originalBeanName) {
      return "scopedTarget." + originalBeanName;
   }

   public static String getOriginalBeanName(@Nullable String targetBeanName) {
      Assert.isTrue(isScopedTarget(targetBeanName), () -> "bean name '" + targetBeanName + "' does not refer to the target of a scoped proxy");
      return targetBeanName.substring(TARGET_NAME_PREFIX_LENGTH);
   }

   @Contract("null -> false")
   public static boolean isScopedTarget(@Nullable String beanName) {
      return beanName != null && beanName.startsWith("scopedTarget.");
   }
}
