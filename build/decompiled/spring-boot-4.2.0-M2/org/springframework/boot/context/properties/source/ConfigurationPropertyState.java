package org.springframework.boot.context.properties.source;

import java.util.function.Predicate;
import org.springframework.util.Assert;

public enum ConfigurationPropertyState {
   PRESENT,
   ABSENT,
   UNKNOWN;

   static <T> ConfigurationPropertyState search(Iterable<T> source, Predicate<T> predicate) {
      Assert.notNull(source, "'source' must not be null");
      Assert.notNull(predicate, "'predicate' must not be null");

      for (T item : source) {
         if (predicate.test(item)) {
            return PRESENT;
         }
      }

      return ABSENT;
   }

   static <T> ConfigurationPropertyState search(T[] source, int startInclusive, int endExclusive, Predicate<T> predicate) {
      Assert.notNull(source, "'source' must not be null");
      Assert.notNull(predicate, "'predicate' must not be null");

      for (int i = startInclusive; i < endExclusive; i++) {
         if (predicate.test(source[i])) {
            return PRESENT;
         }
      }

      return ABSENT;
   }
}
