package org.springframework.beans;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.core.io.ResourceEditor;

class BeanUtilsRuntimeHints implements RuntimeHintsRegistrar {
   public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
      ReflectionHints reflectionHints = hints.reflection();
      reflectionHints.registerType(ResourceEditor.class, new MemberCategory[]{MemberCategory.INVOKE_DECLARED_CONSTRUCTORS});
      reflectionHints.registerTypeIfPresent(
         classLoader, "org.springframework.http.MediaTypeEditor", new MemberCategory[]{MemberCategory.INVOKE_DECLARED_CONSTRUCTORS}
      );
   }
}
