package org.springframework.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.KParameter.Kind;
import kotlin.reflect.jvm.ReflectJvmMapping;
import org.jspecify.annotations.Nullable;

public class KotlinReflectionParameterNameDiscoverer implements ParameterNameDiscoverer {
   @Override
   public @Nullable String @Nullable [] getParameterNames(Method method) {
      if (KotlinDetector.isKotlinType(method.getDeclaringClass())) {
         try {
            KFunction<?> function = ReflectJvmMapping.getKotlinFunction(method);
            return function != null ? this.getParameterNames(function.getParameters()) : null;
         } catch (UnsupportedOperationException var3) {
         }
      }

      return null;
   }

   @Override
   public @Nullable String @Nullable [] getParameterNames(Constructor<?> ctor) {
      if (!ctor.getDeclaringClass().isEnum() && KotlinDetector.isKotlinType(ctor.getDeclaringClass())) {
         try {
            KFunction<?> function = ReflectJvmMapping.getKotlinFunction(ctor);
            if (function != null) {
               return this.getParameterNames(function.getParameters());
            }
         } catch (UnsupportedOperationException var3) {
         }
      }

      return null;
   }

   private String[] getParameterNames(List<KParameter> parameters) {
      String[] parameterNames = parameters.stream()
         .filter(p -> Kind.VALUE.equals(p.getKind()) || Kind.EXTENSION_RECEIVER.equals(p.getKind()))
         .map(p -> Kind.EXTENSION_RECEIVER.equals(p.getKind()) ? "$receiver" : p.getName())
         .toArray(String[]::new);

      for (String parameterName : parameterNames) {
         if (parameterName == null) {
            return null;
         }
      }

      return parameterNames;
   }
}
