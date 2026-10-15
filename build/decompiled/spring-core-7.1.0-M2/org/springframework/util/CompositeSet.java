package org.springframework.util;

import java.util.Set;
import org.jspecify.annotations.Nullable;

final class CompositeSet<E> extends CompositeCollection<E> implements Set<E> {
   CompositeSet(Set<E> first, Set<E> second) {
      super(first, second);
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
