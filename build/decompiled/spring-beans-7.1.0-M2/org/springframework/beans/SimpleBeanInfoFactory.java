package org.springframework.beans;

import java.beans.BeanDescriptor;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.beans.SimpleBeanInfo;
import org.springframework.core.Ordered;

class SimpleBeanInfoFactory implements BeanInfoFactory, Ordered {
   @Override
   public BeanInfo getBeanInfo(Class<?> beanClass) throws IntrospectionException {
      final PropertyDescriptor[] pds = PropertyDescriptorUtils.determineBasicProperties(beanClass)
         .toArray(PropertyDescriptorUtils.EMPTY_PROPERTY_DESCRIPTOR_ARRAY);
      return new SimpleBeanInfo() {
         @Override
         public BeanDescriptor getBeanDescriptor() {
            return new BeanDescriptor(beanClass);
         }

         @Override
         public PropertyDescriptor[] getPropertyDescriptors() {
            return pds;
         }
      };
   }

   public int getOrder() {
      return 2147483646;
   }
}
