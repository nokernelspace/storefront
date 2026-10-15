package org.springframework.aot.hint.support;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

class SpringPropertiesRuntimeHints implements RuntimeHintsRegistrar {
   @Override
   public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
      hints.resources().registerPattern("spring.properties");
   }
}
