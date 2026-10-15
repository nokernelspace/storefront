package org.springframework.core.type.classreading;

import java.io.FileNotFoundException;
import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ClassUtils;

abstract class AbstractMetadataReaderFactory implements MetadataReaderFactory {
   private final ResourceLoader resourceLoader;

   public AbstractMetadataReaderFactory(@Nullable ResourceLoader resourceLoader) {
      this.resourceLoader = resourceLoader != null ? resourceLoader : new DefaultResourceLoader();
   }

   public AbstractMetadataReaderFactory(@Nullable ClassLoader classLoader) {
      this.resourceLoader = classLoader != null ? new DefaultResourceLoader(classLoader) : new DefaultResourceLoader();
   }

   public AbstractMetadataReaderFactory() {
      this.resourceLoader = new DefaultResourceLoader();
   }

   @Override
   public ResourceLoader getResourceLoader() {
      return this.resourceLoader;
   }

   @Override
   public MetadataReader getMetadataReader(String className) throws IOException {
      try {
         String resourcePath = "classpath:" + ClassUtils.convertClassNameToResourcePath(className) + ".class";
         Resource resource = this.resourceLoader.getResource(resourcePath);
         return this.getMetadataReader(resource);
      } catch (FileNotFoundException ex) {
         int lastDotIndex = className.lastIndexOf(46);
         if (lastDotIndex != -1) {
            String innerClassName = className.substring(0, lastDotIndex) + "$" + className.substring(lastDotIndex + 1);
            String innerClassResourcePath = "classpath:" + ClassUtils.convertClassNameToResourcePath(innerClassName) + ".class";
            Resource innerClassResource = this.resourceLoader.getResource(innerClassResourcePath);
            if (innerClassResource.exists()) {
               return this.getMetadataReader(innerClassResource);
            }
         }

         throw ex;
      }
   }
}
