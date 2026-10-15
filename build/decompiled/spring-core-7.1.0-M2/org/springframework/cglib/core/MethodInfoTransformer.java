package org.springframework.cglib.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class MethodInfoTransformer implements Transformer {
   private static final MethodInfoTransformer INSTANCE = new MethodInfoTransformer();

   public static MethodInfoTransformer getInstance() {
      return INSTANCE;
   }

   @Override
   public Object transform(Object value) {
      if (value instanceof Method method) {
         return ReflectUtils.getMethodInfo(method);
      } else if (value instanceof Constructor<?> constructor) {
         return ReflectUtils.getMethodInfo(constructor);
      } else {
         throw new IllegalArgumentException("cannot get method info for " + value);
      }
   }
}
