package org.springframework.web.util;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.core.io.ClassPathResource;

class WebUtilRuntimeHints implements RuntimeHintsRegistrar {
   public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
      hints.resources().registerResource(new ClassPathResource("HtmlCharacterEntityReferences.properties", this.getClass()));
   }
}
