package org.springframework.boot.context.properties;

import org.springframework.beans.factory.BeanFactory;

public abstract class ConstructorBound {
   public static Object from(BeanFactory beanFactory, String beanName, Class<?> beanType) {
      ConfigurationPropertiesBean bean = ConfigurationPropertiesBean.forValueObject(beanType, beanName);
      ConfigurationPropertiesBinder binder = ConfigurationPropertiesBinder.get(beanFactory);

      try {
         return binder.bindOrCreate(bean);
      } catch (Exception ex) {
         throw new ConfigurationPropertiesBindException(bean, ex);
      }
   }
}
