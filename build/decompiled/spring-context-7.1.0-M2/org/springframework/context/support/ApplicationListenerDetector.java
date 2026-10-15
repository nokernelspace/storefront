package org.springframework.context.support;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationEventMulticaster;

class ApplicationListenerDetector implements DestructionAwareBeanPostProcessor, MergedBeanDefinitionPostProcessor {
   private static final Log logger = LogFactory.getLog(ApplicationListenerDetector.class);
   private final transient AbstractApplicationContext applicationContext;
   private final transient Map<String, Boolean> singletonNames = new ConcurrentHashMap<>(256);

   public ApplicationListenerDetector(AbstractApplicationContext applicationContext) {
      this.applicationContext = applicationContext;
   }

   public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
      if (ApplicationListener.class.isAssignableFrom(beanType)) {
         this.singletonNames.put(beanName, beanDefinition.isSingleton());
      }
   }

   public Object postProcessBeforeInitialization(Object bean, String beanName) {
      return bean;
   }

   public Object postProcessAfterInitialization(Object bean, String beanName) {
      if (bean instanceof ApplicationListener<?> applicationListener) {
         Boolean flag = this.singletonNames.get(beanName);
         if (Boolean.TRUE.equals(flag)) {
            this.applicationContext.addApplicationListener(applicationListener);
         } else if (Boolean.FALSE.equals(flag)) {
            if (logger.isWarnEnabled() && !this.applicationContext.containsBean(beanName)) {
               logger.warn(
                  "Inner bean '"
                     + beanName
                     + "' implements ApplicationListener interface but is not reachable for event multicasting by its containing ApplicationContext because it does not have singleton scope. Only top-level listener beans are allowed to be of non-singleton scope."
               );
            }

            this.singletonNames.remove(beanName);
         }
      }

      return bean;
   }

   public void postProcessBeforeDestruction(Object bean, String beanName) {
      if (bean instanceof ApplicationListener<?> applicationListener) {
         try {
            ApplicationEventMulticaster multicaster = this.applicationContext.getApplicationEventMulticaster();
            multicaster.removeApplicationListener(applicationListener);
            multicaster.removeApplicationListenerBean(bean instanceof FactoryBean ? "&" + beanName : beanName);
         } catch (IllegalStateException var5) {
         }
      }
   }

   public boolean requiresDestruction(Object bean) {
      return bean instanceof ApplicationListener;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof ApplicationListenerDetector that && this.applicationContext == that.applicationContext;
   }

   @Override
   public int hashCode() {
      return Objects.hashCode(this.applicationContext);
   }
}
