package org.springframework.core.type.classreading;

import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ResourceLoader;

abstract class MetadataReaderFactoryDelegate {
   static MetadataReaderFactory create(@Nullable ResourceLoader resourceLoader) {
      return new SimpleMetadataReaderFactory(resourceLoader);
   }

   static MetadataReaderFactory create(@Nullable ClassLoader classLoader) {
      return new SimpleMetadataReaderFactory(classLoader);
   }
}
