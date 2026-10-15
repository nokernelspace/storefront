package org.springframework.boot.logging.java;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

class JavaLoggingSystemRuntimeHints implements RuntimeHintsRegistrar {
   public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
      hints.resources().registerPattern("org/springframework/boot/logging/java/logging.properties");
      hints.resources().registerPattern("org/springframework/boot/logging/java/logging-file.properties");
   }
}
