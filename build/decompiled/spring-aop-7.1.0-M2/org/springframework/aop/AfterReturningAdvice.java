package org.springframework.aop;

import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;

public interface AfterReturningAdvice extends AfterAdvice {
   void afterReturning(@Nullable Object returnValue, Method method, @Nullable Object[] args, @Nullable Object target) throws Throwable;
}
