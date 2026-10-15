package org.springframework.beans;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import org.springframework.core.Ordered;
import org.springframework.core.SpringProperties;

public class StandardBeanInfoFactory implements BeanInfoFactory, Ordered {
   public static final String IGNORE_BEANINFO_PROPERTY_NAME = "spring.beaninfo.ignore";
   private static final boolean shouldIntrospectorIgnoreBeaninfoClasses = SpringProperties.getFlag("spring.beaninfo.ignore");

   @Override
   public BeanInfo getBeanInfo(Class<?> beanClass) throws IntrospectionException {
      BeanInfo beanInfo = shouldIntrospectorIgnoreBeaninfoClasses ? Introspector.getBeanInfo(beanClass, 3) : Introspector.getBeanInfo(beanClass);
      Class<?> classToFlush = beanClass;

      do {
         Introspector.flushFromCaches(classToFlush);
         classToFlush = classToFlush.getSuperclass();
      } while (classToFlush != null && classToFlush != Object.class);

      return beanInfo;
   }

   public int getOrder() {
      return Integer.MAX_VALUE;
   }
}
