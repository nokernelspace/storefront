package org.springframework.aop.config;

import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.util.StringUtils;

public class MethodLocatingFactoryBean implements FactoryBean<Method>, BeanFactoryAware {
   private @Nullable String targetBeanName;
   private @Nullable String methodName;
   private @Nullable Method method;

   public void setTargetBeanName(String targetBeanName) {
      this.targetBeanName = targetBeanName;
   }

   public void setMethodName(String methodName) {
      this.methodName = methodName;
   }

   public void setBeanFactory(BeanFactory beanFactory) {
      if (!StringUtils.hasText(this.targetBeanName)) {
         throw new IllegalArgumentException("Property 'targetBeanName' is required");
      }

      if (!StringUtils.hasText(this.methodName)) {
         throw new IllegalArgumentException("Property 'methodName' is required");
      }

      Class<?> beanClass = beanFactory.getType(this.targetBeanName);
      if (beanClass == null) {
         throw new IllegalArgumentException("Can't determine type of bean with name '" + this.targetBeanName + "'");
      }

      this.method = BeanUtils.resolveSignature(this.methodName, beanClass);
      if (this.method == null) {
         throw new IllegalArgumentException("Unable to locate method [" + this.methodName + "] on bean [" + this.targetBeanName + "]");
      }
   }

   public @Nullable Method getObject() throws Exception {
      return this.method;
   }

   public Class<Method> getObjectType() {
      return Method.class;
   }

   public boolean isSingleton() {
      return true;
   }
}
