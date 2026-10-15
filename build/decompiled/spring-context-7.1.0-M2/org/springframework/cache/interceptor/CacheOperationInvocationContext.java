package org.springframework.cache.interceptor;

import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;

public interface CacheOperationInvocationContext<O extends BasicOperation> {
   O getOperation();

   Object getTarget();

   Method getMethod();

   @Nullable Object[] getArgs();
}
