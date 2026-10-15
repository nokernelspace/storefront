package org.springframework.http;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

class HttpMimeTypesRuntimeHints implements RuntimeHintsRegistrar {
   public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
      hints.resources().registerPattern("org/springframework/http/mime.types");
   }
}
