package org.springframework.boot.env;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

class PropertySourceRuntimeHints implements RuntimeHintsRegistrar {
   public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
      hints.reflection()
         .registerTypeIfPresent(classLoader, "org.yaml.snakeyaml.Yaml", typeHint -> typeHint.onReachableType(TypeReference.of(YamlPropertySourceLoader.class)));
   }
}
