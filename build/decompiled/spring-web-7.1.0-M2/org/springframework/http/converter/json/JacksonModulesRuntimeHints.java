package org.springframework.http.converter.json;

import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeHint.Builder;

class JacksonModulesRuntimeHints implements RuntimeHintsRegistrar {
   private static final Consumer<Builder> asJacksonModule = builder -> builder.onReachableType(Jackson2ObjectMapperBuilder.class)
      .withMembers(new MemberCategory[]{MemberCategory.INVOKE_DECLARED_CONSTRUCTORS});

   public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
      hints.reflection()
         .registerTypeIfPresent(classLoader, "com.fasterxml.jackson.datatype.jdk8.Jdk8Module", asJacksonModule)
         .registerTypeIfPresent(classLoader, "com.fasterxml.jackson.datatype.jsr310.JavaTimeModule", asJacksonModule)
         .registerTypeIfPresent(classLoader, "com.fasterxml.jackson.module.kotlin.KotlinModule", asJacksonModule);
   }
}
