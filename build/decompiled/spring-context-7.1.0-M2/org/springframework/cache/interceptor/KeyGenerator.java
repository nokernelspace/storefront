package org.springframework.cache.interceptor;

import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface KeyGenerator {
   Object generate(Object target, Method method, @Nullable Object... params);
}
