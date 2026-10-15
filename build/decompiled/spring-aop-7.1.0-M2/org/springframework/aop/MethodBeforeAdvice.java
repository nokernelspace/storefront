package org.springframework.aop;

import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;

public interface MethodBeforeAdvice extends BeforeAdvice {
   void before(Method method, @Nullable Object[] args, @Nullable Object target) throws Throwable;
}
