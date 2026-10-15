package org.springframework.scheduling.annotation;

import java.lang.annotation.Annotation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.Assert;

@Configuration(proxyBeanMethods = false)
@Role(2)
public class ProxyAsyncConfiguration extends AbstractAsyncConfiguration {
   @Bean(name = "org.springframework.scheduling.config.internalAsyncAnnotationProcessor")
   @Role(2)
   public AsyncAnnotationBeanPostProcessor asyncAdvisor() {
      Assert.state(this.enableAsync != null, "@EnableAsync annotation metadata was not injected");
      AsyncAnnotationBeanPostProcessor bpp = new AsyncAnnotationBeanPostProcessor();
      bpp.configure(this.executor, this.exceptionHandler);
      Class<? extends Annotation> customAsyncAnnotation = this.enableAsync.getClass("annotation");
      if (customAsyncAnnotation != AnnotationUtils.getDefaultValue(EnableAsync.class, "annotation")) {
         bpp.setAsyncAnnotationType(customAsyncAnnotation);
      }

      if (this.enableAsync.getBoolean("proxyTargetClass")) {
         bpp.setProxyTargetClass(true);
      }

      bpp.setOrder((Integer)this.enableAsync.getNumber("order"));
      return bpp;
   }
}
