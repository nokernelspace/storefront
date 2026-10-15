package org.springframework.aot.hint.annotation;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.ReflectionHints;

public class SimpleReflectiveProcessor implements ReflectiveProcessor {
   @Override
   public void registerReflectionHints(ReflectionHints hints, AnnotatedElement element) {
      if (element instanceof Class<?> type) {
         this.registerTypeHint(hints, type);
      } else if (element instanceof Constructor<?> constructor) {
         this.registerConstructorHint(hints, constructor);
      } else if (element instanceof Field field) {
         this.registerFieldHint(hints, field);
      } else if (element instanceof Method method) {
         this.registerMethodHint(hints, method);
      }
   }

   protected void registerTypeHint(ReflectionHints hints, Class<?> type) {
      hints.registerType(type);
   }

   protected void registerConstructorHint(ReflectionHints hints, Constructor<?> constructor) {
      hints.registerConstructor(constructor, ExecutableMode.INVOKE);
   }

   protected void registerFieldHint(ReflectionHints hints, Field field) {
      hints.registerField(field);
   }

   protected void registerMethodHint(ReflectionHints hints, Method method) {
      hints.registerMethod(method, ExecutableMode.INVOKE);
   }
}
