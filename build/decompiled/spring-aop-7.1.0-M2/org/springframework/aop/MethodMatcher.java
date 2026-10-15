package org.springframework.aop;

import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;

public interface MethodMatcher {
   MethodMatcher TRUE = TrueMethodMatcher.INSTANCE;

   boolean matches(Method method, Class<?> targetClass);

   boolean isRuntime();

   boolean matches(Method method, Class<?> targetClass, @Nullable Object... args);
}
