package org.springframework.cache.interceptor;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public class CompositeCacheOperationSource implements CacheOperationSource, Serializable {
   private final CacheOperationSource[] cacheOperationSources;

   public CompositeCacheOperationSource(CacheOperationSource... cacheOperationSources) {
      Assert.notEmpty(cacheOperationSources, "CacheOperationSource array must not be empty");
      this.cacheOperationSources = cacheOperationSources;
   }

   public final CacheOperationSource[] getCacheOperationSources() {
      return this.cacheOperationSources;
   }

   @Override
   public boolean isCandidateClass(Class<?> targetClass) {
      for (CacheOperationSource source : this.cacheOperationSources) {
         if (source.isCandidateClass(targetClass)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public boolean hasCacheOperations(Method method, @Nullable Class<?> targetClass) {
      for (CacheOperationSource source : this.cacheOperationSources) {
         if (source.hasCacheOperations(method, targetClass)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public @Nullable Collection<CacheOperation> getCacheOperations(Method method, @Nullable Class<?> targetClass) {
      Collection<CacheOperation> ops = null;

      for (CacheOperationSource source : this.cacheOperationSources) {
         Collection<CacheOperation> cacheOperations = source.getCacheOperations(method, targetClass);
         if (cacheOperations != null) {
            if (ops == null) {
               ops = new ArrayList<>();
            }

            ops.addAll(cacheOperations);
         }
      }

      return ops;
   }
}
