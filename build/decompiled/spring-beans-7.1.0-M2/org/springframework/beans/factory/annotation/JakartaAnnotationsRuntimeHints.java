package org.springframework.beans.factory.annotation;

import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

class JakartaAnnotationsRuntimeHints implements RuntimeHintsRegistrar {
   public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
      Stream.of("jakarta.inject.Inject", "jakarta.inject.Provider", "jakarta.inject.Qualifier")
         .forEach(typeName -> hints.reflection().registerType(TypeReference.of(typeName), new MemberCategory[0]));
   }
}
