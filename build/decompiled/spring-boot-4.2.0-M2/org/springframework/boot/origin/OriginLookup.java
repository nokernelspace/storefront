package org.springframework.boot.origin;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface OriginLookup<K> {
   @Nullable Origin getOrigin(K key);

   static <K> @Nullable Origin getOrigin(@Nullable Object source, K key) {
      if (!(source instanceof OriginLookup)) {
         return null;
      }

      try {
         return ((OriginLookup)source).getOrigin(key);
      } catch (Throwable ex) {
         return null;
      }
   }
}
