package org.springframework.resilience.annotation;

import org.jspecify.annotations.Nullable;
import org.springframework.aop.framework.ProxyProcessorSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.context.annotation.Role;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

@Configuration(proxyBeanMethods = false)
@Role(2)
public class ResilientMethodsConfiguration implements ImportAware {
   private @Nullable AnnotationAttributes enableResilientMethods;

   @Override
   public void setImportMetadata(AnnotationMetadata importMetadata) {
      this.enableResilientMethods = AnnotationAttributes.fromMap(importMetadata.getAnnotationAttributes(EnableResilientMethods.class.getName()));
   }

   private void configureProxySupport(ProxyProcessorSupport proxySupport) {
      if (this.enableResilientMethods != null) {
         if (this.enableResilientMethods.getBoolean("proxyTargetClass")) {
            proxySupport.setProxyTargetClass(true);
         }

         proxySupport.setOrder((Integer)this.enableResilientMethods.getNumber("order"));
      }
   }

   @Bean(name = "org.springframework.resilience.annotation.internalRetryAnnotationProcessor")
   @Role(2)
   public RetryAnnotationBeanPostProcessor retryAdvisor() {
      RetryAnnotationBeanPostProcessor bpp = new RetryAnnotationBeanPostProcessor();
      this.configureProxySupport(bpp);
      return bpp;
   }

   @Bean(name = "org.springframework.resilience.annotation.internalConcurrencyLimitProcessor")
   @Role(2)
   public ConcurrencyLimitBeanPostProcessor concurrencyLimitAdvisor() {
      ConcurrencyLimitBeanPostProcessor bpp = new ConcurrencyLimitBeanPostProcessor();
      this.configureProxySupport(bpp);
      return bpp;
   }
}
