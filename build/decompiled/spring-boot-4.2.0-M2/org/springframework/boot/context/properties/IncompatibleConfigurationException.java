package org.springframework.boot.context.properties;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class IncompatibleConfigurationException extends RuntimeException {
   private final List<String> incompatibleKeys;

   public IncompatibleConfigurationException(String... incompatibleKeys) {
      super("The following configuration properties have incompatible values: " + Arrays.toString(incompatibleKeys));
      this.incompatibleKeys = Arrays.asList(incompatibleKeys);
   }

   public Collection<String> getIncompatibleKeys() {
      return this.incompatibleKeys;
   }
}
