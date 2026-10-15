package org.springframework.core.type.classreading;

import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

public class SimpleMetadataReaderFactory extends AbstractMetadataReaderFactory {
   public SimpleMetadataReaderFactory() {
   }

   public SimpleMetadataReaderFactory(@Nullable ResourceLoader resourceLoader) {
      super(resourceLoader);
   }

   public SimpleMetadataReaderFactory(@Nullable ClassLoader classLoader) {
      super(classLoader);
   }

   @Override
   public MetadataReader getMetadataReader(Resource resource) throws IOException {
      return new SimpleMetadataReader(resource, this.getResourceLoader().getClassLoader());
   }
}
