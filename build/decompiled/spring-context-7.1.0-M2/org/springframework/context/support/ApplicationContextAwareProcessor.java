package org.springframework.context.support;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.Aware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.EmbeddedValueResolver;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.ApplicationStartupAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.util.StringValueResolver;

class ApplicationContextAwareProcessor implements BeanPostProcessor {
   private final ConfigurableApplicationContext applicationContext;
   private final StringValueResolver embeddedValueResolver;

   public ApplicationContextAwareProcessor(ConfigurableApplicationContext applicationContext) {
      this.applicationContext = applicationContext;
      this.embeddedValueResolver = new EmbeddedValueResolver(applicationContext.getBeanFactory());
   }

   public @Nullable Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
      if (bean instanceof Aware) {
         this.invokeAwareInterfaces(bean);
      }

      return bean;
   }

   private void invokeAwareInterfaces(Object bean) {
      if (bean instanceof EnvironmentAware environmentAware) {
         environmentAware.setEnvironment(this.applicationContext.getEnvironment());
      }

      if (bean instanceof EmbeddedValueResolverAware embeddedValueResolverAware) {
         embeddedValueResolverAware.setEmbeddedValueResolver(this.embeddedValueResolver);
      }

      if (bean instanceof ResourceLoaderAware resourceLoaderAware) {
         resourceLoaderAware.setResourceLoader(this.applicationContext);
      }

      if (bean instanceof ApplicationEventPublisherAware applicationEventPublisherAware) {
         applicationEventPublisherAware.setApplicationEventPublisher(this.applicationContext);
      }

      if (bean instanceof MessageSourceAware messageSourceAware) {
         messageSourceAware.setMessageSource(this.applicationContext);
      }

      if (bean instanceof ApplicationStartupAware applicationStartupAware) {
         applicationStartupAware.setApplicationStartup(this.applicationContext.getApplicationStartup());
      }

      if (bean instanceof ApplicationContextAware applicationContextAware) {
         applicationContextAware.setApplicationContext(this.applicationContext);
      }
   }
}
