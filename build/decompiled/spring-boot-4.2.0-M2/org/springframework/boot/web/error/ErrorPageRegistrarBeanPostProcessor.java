package org.springframework.boot.web.error;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.util.Assert;

public class ErrorPageRegistrarBeanPostProcessor implements BeanPostProcessor, BeanFactoryAware {
   private ListableBeanFactory beanFactory;
   private @Nullable List<ErrorPageRegistrar> registrars;

   public void setBeanFactory(BeanFactory beanFactory) {
      Assert.isInstanceOf(ListableBeanFactory.class, beanFactory, "'beanFactory' must be a ListableBeanFactory");
      this.beanFactory = (ListableBeanFactory)beanFactory;
   }

   public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
      if (bean instanceof ErrorPageRegistry errorPageRegistry) {
         this.postProcessBeforeInitialization(errorPageRegistry);
      }

      return bean;
   }

   public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
      return bean;
   }

   private void postProcessBeforeInitialization(ErrorPageRegistry registry) {
      for (ErrorPageRegistrar registrar : this.getRegistrars()) {
         registrar.registerErrorPages(registry);
      }
   }

   private Collection<ErrorPageRegistrar> getRegistrars() {
      if (this.registrars == null) {
         this.registrars = new ArrayList<>(this.beanFactory.getBeansOfType(ErrorPageRegistrar.class, false, false).values());
         this.registrars.sort(AnnotationAwareOrderComparator.INSTANCE);
         this.registrars = Collections.unmodifiableList(this.registrars);
      }

      return this.registrars;
   }
}
