package org.springframework.boot.context.properties.source;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import org.springframework.core.env.PropertySource;

public class UnboundElementsSourceFilter implements Function<ConfigurationPropertySource, Boolean> {
   private static final Set<String> BENIGN_PROPERTY_SOURCE_NAMES = Collections.unmodifiableSet(
      new HashSet<>(Arrays.asList("systemEnvironment", "systemProperties"))
   );

   public Boolean apply(ConfigurationPropertySource configurationPropertySource) {
      if (configurationPropertySource.getUnderlyingSource() instanceof PropertySource<?> propertySource) {
         String name = propertySource.getName();
         return !BENIGN_PROPERTY_SOURCE_NAMES.contains(name);
      } else {
         return true;
      }
   }
}
