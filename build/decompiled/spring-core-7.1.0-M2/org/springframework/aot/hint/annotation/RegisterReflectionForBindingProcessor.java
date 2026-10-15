package org.springframework.aot.hint.annotation;

import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.ReflectionHints;

class RegisterReflectionForBindingProcessor extends RegisterReflectionReflectiveProcessor {
   private final BindingReflectionHintsRegistrar bindingRegistrar = new BindingReflectionHintsRegistrar();

   @Override
   protected void registerReflectionHints(ReflectionHints hints, Class<?> target, MemberCategory[] memberCategories) {
      this.bindingRegistrar.registerReflectionHints(hints, target);
   }
}
