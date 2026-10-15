package org.springframework.core.type.filter;

import java.io.IOException;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

public abstract class AbstractClassTestingTypeFilter implements TypeFilter {
   @Override
   public final boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) throws IOException {
      return this.match(metadataReader.getClassMetadata());
   }

   protected abstract boolean match(ClassMetadata metadata);
}
