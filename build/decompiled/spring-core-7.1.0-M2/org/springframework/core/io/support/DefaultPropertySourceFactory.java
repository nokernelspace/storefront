package org.springframework.core.io.support;

import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.PropertySource;

public class DefaultPropertySourceFactory implements PropertySourceFactory {
   @Override
   public PropertySource<?> createPropertySource(@Nullable String name, EncodedResource resource) throws IOException {
      return name != null ? new ResourcePropertySource(name, resource) : new ResourcePropertySource(resource);
   }
}
