package org.springframework.util;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public class InstanceFilter<T> {
   protected final Collection<? extends T> includes;
   protected final Collection<? extends T> excludes;
   protected final boolean matchIfEmpty;

   public InstanceFilter(@Nullable Collection<? extends T> includes, @Nullable Collection<? extends T> excludes) {
      this(includes, excludes, true);
   }

   public InstanceFilter(@Nullable Collection<? extends T> includes, @Nullable Collection<? extends T> excludes, boolean matchIfEmpty) {
      this.includes = includes != null ? Collections.unmodifiableCollection(includes) : Set.of();
      this.excludes = excludes != null ? Collections.unmodifiableCollection(excludes) : Set.of();
      this.matchIfEmpty = matchIfEmpty;
   }

   public boolean match(T instance) {
      Assert.notNull(instance, "Instance to match must not be null");
      boolean emptyIncludes = this.includes.isEmpty();
      boolean emptyExcludes = this.excludes.isEmpty();
      if (emptyIncludes && emptyExcludes) {
         return this.matchIfEmpty;
      } else {
         return !emptyExcludes && this.match(instance, this.excludes) ? false : emptyIncludes || this.match(instance, this.includes);
      }
   }

   protected boolean match(T instance, T candidate) {
      return instance.equals(candidate);
   }

   protected boolean match(T instance, Collection<? extends T> candidates) {
      for (T candidate : candidates) {
         if (this.match(instance, candidate)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder(this.getClass().getSimpleName());
      sb.append(": includes=").append(this.includes);
      sb.append(", excludes=").append(this.excludes);
      sb.append(", matchIfEmpty=").append(this.matchIfEmpty);
      return sb.toString();
   }
}
