package org.springframework.aot.hint;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface RuntimeHintsRegistrar {
   void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader);
}
