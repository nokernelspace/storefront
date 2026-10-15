package org.springframework.core.type.classreading;

import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

public interface MetadataReaderFactory {
   MetadataReader getMetadataReader(String className) throws IOException;

   MetadataReader getMetadataReader(Resource resource) throws IOException;

   ResourceLoader getResourceLoader();

   static MetadataReaderFactory create(@Nullable ResourceLoader resourceLoader) {
      return MetadataReaderFactoryDelegate.create(resourceLoader);
   }

   static MetadataReaderFactory create(@Nullable ClassLoader classLoader) {
      return MetadataReaderFactoryDelegate.create(classLoader);
   }
}
