package org.springframework.util;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

final class FilteredIterator<E> implements Iterator<E> {
   private final Iterator<E> delegate;
   private final Predicate<E> filter;
   private @Nullable E next;
   private boolean hasNext;

   public FilteredIterator(Iterator<E> delegate, Predicate<E> filter) {
      Assert.notNull(delegate, "Delegate must not be null");
      Assert.notNull(filter, "Filter must not be null");
      this.delegate = delegate;
      this.filter = filter;
   }

   @Override
   public boolean hasNext() {
      return this.hasNext || this.setNext();
   }

   @Override
   public @Nullable E next() {
      if (!this.hasNext && !this.setNext()) {
         throw new NoSuchElementException();
      }

      this.hasNext = false;
      return this.next;
   }

   private boolean setNext() {
      while (this.delegate.hasNext()) {
         E next = this.delegate.next();
         if (this.filter.test(next)) {
            this.next = next;
            this.hasNext = true;
            return true;
         }
      }

      return false;
   }
}
