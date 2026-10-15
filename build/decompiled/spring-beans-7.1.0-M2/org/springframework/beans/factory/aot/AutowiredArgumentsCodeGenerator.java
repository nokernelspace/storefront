package org.springframework.beans.factory.aot;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Predicate;
import org.springframework.javapoet.CodeBlock;
import org.springframework.javapoet.CodeBlock.Builder;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

public class AutowiredArgumentsCodeGenerator {
   private final Class<?> target;
   private final Executable executable;

   public AutowiredArgumentsCodeGenerator(Class<?> target, Executable executable) {
      this.target = target;
      this.executable = executable;
   }

   public CodeBlock generateCode(Class<?>[] parameterTypes) {
      return this.generateCode(parameterTypes, 0, "args");
   }

   public CodeBlock generateCode(Class<?>[] parameterTypes, int startIndex) {
      return this.generateCode(parameterTypes, startIndex, "args");
   }

   public CodeBlock generateCode(Class<?>[] parameterTypes, int startIndex, String variableName) {
      Assert.notNull(parameterTypes, "'parameterTypes' must not be null");
      Assert.notNull(variableName, "'variableName' must not be null");
      boolean ambiguous = this.isAmbiguous();
      Builder code = CodeBlock.builder();

      for (int i = startIndex; i < parameterTypes.length; i++) {
         code.add(i > startIndex ? ", " : "", new Object[0]);
         if (!ambiguous) {
            code.add("$L.get($L)", new Object[]{variableName, i});
         } else {
            code.add("$L.get($L, $T.class)", new Object[]{variableName, i, parameterTypes[i]});
         }
      }

      return code.build();
   }

   private boolean isAmbiguous() {
      if (this.executable instanceof Constructor<?> constructor) {
         return Arrays.stream(this.target.getDeclaredConstructors()).filter(Predicate.not(constructor::equals)).anyMatch(this::hasSameParameterCount);
      } else {
         return this.executable instanceof Method method
            ? Arrays.stream(ReflectionUtils.getAllDeclaredMethods(this.target))
               .filter(Predicate.not(method::equals))
               .filter(candidate -> candidate.getName().equals(method.getName()))
               .anyMatch(this::hasSameParameterCount)
            : true;
      }
   }

   private boolean hasSameParameterCount(Executable executable) {
      return this.executable.getParameterCount() == executable.getParameterCount();
   }
}
