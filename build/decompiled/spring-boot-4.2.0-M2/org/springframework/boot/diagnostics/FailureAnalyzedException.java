package org.springframework.boot.diagnostics;

import org.jspecify.annotations.Nullable;

public class FailureAnalyzedException extends RuntimeException {
   private final @Nullable String action;

   public FailureAnalyzedException(String description, @Nullable String action) {
      super(description);
      this.action = action;
   }

   public FailureAnalyzedException(String description, @Nullable String action, Throwable cause) {
      super(description, cause);
      this.action = action;
   }

   public FailureAnalysis analysis() {
      return new FailureAnalysis(this.getMessage(), this.action, this);
   }

   static @Nullable FailureAnalysis analyze(Throwable failure) {
      while (failure != null) {
         if (failure instanceof FailureAnalyzedException failureAnalyzedException) {
            return failureAnalyzedException.analysis();
         }

         failure = failure.getCause();
      }

      return null;
   }
}
