package org.springframework.beans.factory.annotation;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.wiring.BeanWiringInfo;
import org.springframework.beans.factory.wiring.BeanWiringInfoResolver;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

public class AnnotationBeanWiringInfoResolver implements BeanWiringInfoResolver {
   @Override
   public @Nullable BeanWiringInfo resolveWiringInfo(Object beanInstance) {
      Assert.notNull(beanInstance, "Bean instance must not be null");
      Configurable annotation = beanInstance.getClass().getAnnotation(Configurable.class);
      return annotation != null ? this.buildWiringInfo(beanInstance, annotation) : null;
   }

   protected BeanWiringInfo buildWiringInfo(Object beanInstance, Configurable annotation) {
      if (!Autowire.NO.equals(annotation.autowire())) {
         return new BeanWiringInfo(annotation.autowire().value(), annotation.dependencyCheck());
      } else {
         return !annotation.value().isEmpty() ? new BeanWiringInfo(annotation.value(), false) : new BeanWiringInfo(this.getDefaultBeanName(beanInstance), true);
      }
   }

   protected String getDefaultBeanName(Object beanInstance) {
      return ClassUtils.getUserClass(beanInstance).getName();
   }
}
