package org.springframework.util;

import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

final class FilteredSet<E> extends FilteredCollection<E> implements Set<E> {
   public FilteredSet(Set<E> delegate, Predicate<E> filter) {
      super(delegate, filter);
   }

   @Override
   public boolean equals(@Nullable Object other) {
      if (this == other) {
         return true;
      }

      if (other instanceof Set<?> otherSet && this.size() == otherSet.size()) {
         try {
            return this.containsAll(otherSet);
         } catch (ClassCastException | NullPointerException var4) {
         }
      }

      return false;
   }

   @Override
   public int hashCode() {
      int hashCode = 0;

      for (E obj : this) {
         if (obj != null) {
            hashCode += obj.hashCode();
         }
      }

      return hashCode;
   }
}
