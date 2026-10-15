package org.springframework.context.annotation;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.util.Assert;

public class ScannedGenericBeanDefinition extends GenericBeanDefinition implements AnnotatedBeanDefinition {
   private final AnnotationMetadata metadata;

   public ScannedGenericBeanDefinition(MetadataReader metadataReader) {
      Assert.notNull(metadataReader, "MetadataReader must not be null");
      this.metadata = metadataReader.getAnnotationMetadata();
      this.setBeanClassName(this.metadata.getClassName());
      this.setResource(metadataReader.getResource());
   }

   public final AnnotationMetadata getMetadata() {
      return this.metadata;
   }

   public @Nullable MethodMetadata getFactoryMethodMetadata() {
      return null;
   }
}
