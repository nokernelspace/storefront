package org.springframework.boot.diagnostics;

import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.util.Assert;

public abstract class AbstractFailureAnalyzer<T extends Throwable> implements FailureAnalyzer {
   @Override
   public @Nullable FailureAnalysis analyze(Throwable failure) {
      T cause = this.findCause(failure, (Class<T>)this.getCauseType());
      return cause != null ? this.analyze(failure, cause) : null;
   }

   protected abstract @Nullable FailureAnalysis analyze(Throwable rootFailure, T cause);

   protected Class<? extends T> getCauseType() {
      Class<? extends T> type = ResolvableType.forClass(AbstractFailureAnalyzer.class, this.getClass()).resolveGeneric(new int[0]);
      Assert.state(type != null, "Unable to resolve generic");
      return type;
   }

   protected final <E extends Throwable> @Nullable E findCause(@Nullable Throwable failure, Class<E> type) {
      while (failure != null) {
         if (type.isInstance(failure)) {
            return (E)failure;
         }

         failure = failure.getCause();
      }

      return null;
   }
}
