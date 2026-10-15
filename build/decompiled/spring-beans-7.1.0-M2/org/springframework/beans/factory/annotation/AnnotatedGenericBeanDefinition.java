package org.springframework.beans.factory.annotation;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.util.Assert;

public class AnnotatedGenericBeanDefinition extends GenericBeanDefinition implements AnnotatedBeanDefinition {
   private final AnnotationMetadata metadata;
   private @Nullable MethodMetadata factoryMethodMetadata;

   public AnnotatedGenericBeanDefinition(Class<?> beanClass) {
      this.setBeanClass(beanClass);
      this.metadata = AnnotationMetadata.introspect(beanClass);
   }

   public AnnotatedGenericBeanDefinition(AnnotationMetadata metadata) {
      Assert.notNull(metadata, "AnnotationMetadata must not be null");
      if (metadata instanceof StandardAnnotationMetadata sam) {
         this.setBeanClass(sam.getIntrospectedClass());
      } else {
         this.setBeanClassName(metadata.getClassName());
      }

      this.metadata = metadata;
   }

   public AnnotatedGenericBeanDefinition(AnnotationMetadata metadata, MethodMetadata factoryMethodMetadata) {
      this(metadata);
      Assert.notNull(factoryMethodMetadata, "MethodMetadata must not be null");
      this.setFactoryMethodName(factoryMethodMetadata.getMethodName());
      this.factoryMethodMetadata = factoryMethodMetadata;
   }

   @Override
   public final AnnotationMetadata getMetadata() {
      return this.metadata;
   }

   @Override
   public final @Nullable MethodMetadata getFactoryMethodMetadata() {
      return this.factoryMethodMetadata;
   }
}
