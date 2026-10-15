package org.springframework.context.expression;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.expression.AccessException;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.util.Assert;

public class BeanFactoryResolver implements BeanResolver {
   private final BeanFactory beanFactory;

   public BeanFactoryResolver(BeanFactory beanFactory) {
      Assert.notNull(beanFactory, "BeanFactory must not be null");
      this.beanFactory = beanFactory;
   }

   public Object resolve(EvaluationContext context, String beanName) throws AccessException {
      try {
         return this.beanFactory.getBean(beanName);
      } catch (BeansException ex) {
         throw new AccessException("Could not resolve bean reference against BeanFactory", ex);
      }
   }
}
