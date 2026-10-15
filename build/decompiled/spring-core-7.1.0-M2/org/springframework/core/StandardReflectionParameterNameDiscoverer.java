package org.springframework.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.jspecify.annotations.Nullable;

public class StandardReflectionParameterNameDiscoverer implements ParameterNameDiscoverer {
   @Override
   public @Nullable String @Nullable [] getParameterNames(Method method) {
      return this.getParameterNames(method.getParameters());
   }

   @Override
   public @Nullable String @Nullable [] getParameterNames(Constructor<?> ctor) {
      return this.getParameterNames(ctor.getParameters());
   }

   private String @Nullable [] getParameterNames(Parameter[] parameters) {
      String[] parameterNames = new String[parameters.length];

      for (int i = 0; i < parameters.length; i++) {
         Parameter param = parameters[i];
         if (!param.isNamePresent()) {
            return null;
         }

         parameterNames[i] = param.getName();
      }

      return parameterNames;
   }
}
