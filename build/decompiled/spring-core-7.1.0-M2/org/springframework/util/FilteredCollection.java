package org.springframework.util;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Predicate;

class FilteredCollection<E> extends AbstractCollection<E> {
   private final Collection<E> delegate;
   private final Predicate<E> filter;

   public FilteredCollection(Collection<E> delegate, Predicate<E> filter) {
      Assert.notNull(delegate, "Delegate must not be null");
      Assert.notNull(filter, "Filter must not be null");
      this.delegate = delegate;
      this.filter = filter;
   }

   @Override
   public int size() {
      int size = 0;

      for (E e : this.delegate) {
         if (this.filter.test(e)) {
            size++;
         }
      }

      return size;
   }

   @Override
   public Iterator<E> iterator() {
      return new FilteredIterator<>(this.delegate.iterator(), this.filter);
   }

   @Override
   public boolean add(E e) {
      boolean added = this.delegate.add(e);
      return added && this.filter.test(e);
   }

   @Override
   public boolean remove(Object o) {
      boolean removed = this.delegate.remove(o);
      return removed && this.filter.test((E)o);
   }

   @Override
   public boolean contains(Object o) {
      return this.delegate.contains(o) ? this.filter.test((E)o) : false;
   }

   @Override
   public void clear() {
      this.delegate.clear();
   }
}
