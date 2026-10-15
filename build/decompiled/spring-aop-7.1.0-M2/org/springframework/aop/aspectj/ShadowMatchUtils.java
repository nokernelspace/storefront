package org.springframework.aop.aspectj;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.aspectj.weaver.tools.ShadowMatch;
import org.jspecify.annotations.Nullable;

public abstract class ShadowMatchUtils {
   private static final Map<Object, ShadowMatch> shadowMatchCache = new ConcurrentHashMap<>(256);

   static @Nullable ShadowMatch getShadowMatch(Object key) {
      return shadowMatchCache.get(key);
   }

   static ShadowMatch setShadowMatch(Object key, ShadowMatch shadowMatch) {
      ShadowMatch existing = shadowMatchCache.putIfAbsent(key, shadowMatch);
      return existing != null ? existing : shadowMatch;
   }

   public static void clearCache() {
      shadowMatchCache.clear();
   }
}
