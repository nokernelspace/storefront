package org.springframework.cache.interceptor;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ObjectUtils;
import org.springframework.util.PatternMatchUtils;

public class NameMatchCacheOperationSource implements CacheOperationSource, Serializable {
   protected static final Log logger = LogFactory.getLog(NameMatchCacheOperationSource.class);
   private final Map<String, Collection<CacheOperation>> nameMap = new LinkedHashMap<>();

   public void setNameMap(Map<String, Collection<CacheOperation>> nameMap) {
      nameMap.forEach(this::addCacheMethod);
   }

   public void addCacheMethod(String methodName, Collection<CacheOperation> ops) {
      if (logger.isDebugEnabled()) {
         logger.debug("Adding method [" + methodName + "] with cache operations [" + ops + "]");
      }

      this.nameMap.put(methodName, ops);
   }

   @Override
   public @Nullable Collection<CacheOperation> getCacheOperations(Method method, @Nullable Class<?> targetClass) {
      String methodName = method.getName();
      Collection<CacheOperation> ops = this.nameMap.get(methodName);
      if (ops == null) {
         String bestNameMatch = null;

         for (String mappedName : this.nameMap.keySet()) {
            if (this.isMatch(methodName, mappedName) && (bestNameMatch == null || bestNameMatch.length() <= mappedName.length())) {
               ops = this.nameMap.get(mappedName);
               bestNameMatch = mappedName;
            }
         }
      }

      return ops;
   }

   protected boolean isMatch(String methodName, String mappedName) {
      return PatternMatchUtils.simpleMatch(mappedName, methodName);
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof NameMatchCacheOperationSource otherCos && ObjectUtils.nullSafeEquals(this.nameMap, otherCos.nameMap);
   }

   @Override
   public int hashCode() {
      return NameMatchCacheOperationSource.class.hashCode();
   }

   @Override
   public String toString() {
      return this.getClass().getName() + ": " + this.nameMap;
   }
}
