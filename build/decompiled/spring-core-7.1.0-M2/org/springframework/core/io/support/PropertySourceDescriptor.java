package org.springframework.core.io.support;

import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record PropertySourceDescriptor(
   List<String> locations,
   boolean ignoreResourceNotFound,
   @Nullable String name,
   @Nullable Class<? extends PropertySourceFactory> propertySourceFactory,
   @Nullable String encoding
) {
   public PropertySourceDescriptor(String... locations) {
      this(Arrays.asList(locations), false, null, null, null);
   }
}
