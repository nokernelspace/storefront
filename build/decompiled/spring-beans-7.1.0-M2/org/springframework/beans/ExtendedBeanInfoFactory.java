package org.springframework.beans;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.lang.reflect.Method;

public class ExtendedBeanInfoFactory extends StandardBeanInfoFactory {
   @Override
   public BeanInfo getBeanInfo(Class<?> beanClass) throws IntrospectionException {
      BeanInfo beanInfo = super.getBeanInfo(beanClass);
      return this.supports(beanClass) ? new ExtendedBeanInfo(beanInfo) : beanInfo;
   }

   private boolean supports(Class<?> beanClass) {
      for (Method method : beanClass.getMethods()) {
         if (ExtendedBeanInfo.isCandidateWriteMethod(method)) {
            return true;
         }
      }

      return false;
   }
}
