package org.springframework.context.annotation;

import org.springframework.beans.factory.BeanRegistrar;

public interface AnnotationConfigRegistry {
   void register(BeanRegistrar... registrars);

   void register(Class<?>... componentClasses);

   void scan(String... basePackages);
}
