package org.springframework.util;

import java.util.Collection;
import org.jspecify.annotations.Nullable;

public class ExceptionTypeFilter extends InstanceFilter<Class<? extends Throwable>> {
   public ExceptionTypeFilter(@Nullable Collection<Class<? extends Throwable>> includes, @Nullable Collection<Class<? extends Throwable>> excludes) {
      super(includes, excludes);
   }

   public ExceptionTypeFilter(
      @Nullable Collection<? extends Class<? extends Throwable>> includes,
      @Nullable Collection<? extends Class<? extends Throwable>> excludes,
      boolean matchIfEmpty
   ) {
      super(includes, excludes, matchIfEmpty);
   }

   public boolean match(Throwable exception) {
      return this.match(exception, false);
   }

   public boolean match(Throwable exception, boolean traverseCauses) {
      return traverseCauses ? this.matchTraversingCauses(exception) : this.match((Class<? extends Throwable>)exception.getClass());
   }

   private boolean matchTraversingCauses(Throwable exception) {
      Assert.notNull(exception, "Throwable to match must not be null");
      boolean emptyIncludes = super.includes.isEmpty();
      boolean emptyExcludes = super.excludes.isEmpty();
      if (emptyIncludes && emptyExcludes) {
         return super.matchIfEmpty;
      } else {
         return !emptyExcludes && this.matchTraversingCauses(exception, super.excludes)
            ? false
            : emptyIncludes || this.matchTraversingCauses(exception, super.includes);
      }
   }

   private boolean matchTraversingCauses(Throwable exception, Collection<? extends Class<? extends Throwable>> candidateTypes) {
      for (Class<? extends Throwable> candidateType : candidateTypes) {
         for (Throwable current = exception; current != null; current = current.getCause()) {
            if (this.match((Class<? extends Throwable>)current.getClass(), candidateType)) {
               return true;
            }
         }
      }

      return false;
   }

   protected boolean match(Class<? extends Throwable> instance, Class<? extends Throwable> candidate) {
      return candidate.isAssignableFrom(instance);
   }
}
