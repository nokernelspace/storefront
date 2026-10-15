package org.springframework.cache.interceptor;

import java.util.Collection;
import org.springframework.cache.Cache;

@FunctionalInterface
public interface CacheResolver {
   Collection<? extends Cache> resolveCaches(CacheOperationInvocationContext<?> context);
}
