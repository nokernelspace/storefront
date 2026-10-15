package org.springframework.aot.hint.support;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

class PathMatchingResourcePatternResolverRuntimeHints implements RuntimeHintsRegistrar {
   @Override
   public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
      hints.reflection().registerType(TypeReference.of("org.eclipse.core.runtime.FileLocator"));
   }
}
