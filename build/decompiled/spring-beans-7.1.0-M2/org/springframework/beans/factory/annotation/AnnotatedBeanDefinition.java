package org.springframework.beans.factory.annotation;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;

public interface AnnotatedBeanDefinition extends BeanDefinition {
   AnnotationMetadata getMetadata();

   @Nullable MethodMetadata getFactoryMethodMetadata();
}
