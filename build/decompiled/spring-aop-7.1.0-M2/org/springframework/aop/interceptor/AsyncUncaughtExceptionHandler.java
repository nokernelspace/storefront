package org.springframework.aop.interceptor;

import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface AsyncUncaughtExceptionHandler {
   void handleUncaughtException(Throwable ex, Method method, @Nullable Object... params);
}
