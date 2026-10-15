package org.springframework.cache.interceptor;

import java.util.Set;

public interface BasicOperation {
   Set<String> getCacheNames();
}
