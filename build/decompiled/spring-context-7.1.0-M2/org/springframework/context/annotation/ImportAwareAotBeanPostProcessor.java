package org.springframework.context.annotation;

import java.io.IOException;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ClassUtils;

public final class ImportAwareAotBeanPostProcessor implements BeanPostProcessor, PriorityOrdered {
   private final MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory();
   private final Map<String, String> importsMapping;

   public ImportAwareAotBeanPostProcessor(Map<String, String> importsMapping) {
      this.importsMapping = Map.copyOf(importsMapping);
   }

   public Object postProcessBeforeInitialization(Object bean, String beanName) {
      if (bean instanceof ImportAware importAware) {
         this.setAnnotationMetadata(importAware);
      }

      return bean;
   }

   public int getOrder() {
      return Integer.MAX_VALUE;
   }

   private void setAnnotationMetadata(ImportAware instance) {
      String importingClass = this.getImportingClassFor(instance);
      if (importingClass != null) {
         try {
            MetadataReader metadataReader = this.metadataReaderFactory.getMetadataReader(importingClass);
            instance.setImportMetadata(metadataReader.getAnnotationMetadata());
         } catch (IOException ex) {
            throw new IllegalStateException(String.format("Failed to read metadata for '%s'", importingClass), ex);
         }
      }
   }

   private @Nullable String getImportingClassFor(ImportAware instance) {
      String target = ClassUtils.getUserClass(instance).getName();
      return this.importsMapping.get(target);
   }
}
