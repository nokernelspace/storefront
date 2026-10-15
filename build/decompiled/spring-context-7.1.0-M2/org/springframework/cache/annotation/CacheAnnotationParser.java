package org.springframework.cache.annotation;

import java.lang.reflect.Method;
import java.util.Collection;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.interceptor.CacheOperation;

public interface CacheAnnotationParser {
   default boolean isCandidateClass(Class<?> targetClass) {
      return true;
   }

   @Nullable Collection<CacheOperation> parseCacheAnnotations(Class<?> type);

   @Nullable Collection<CacheOperation> parseCacheAnnotations(Method method);
}
