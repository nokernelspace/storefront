package org.springframework.boot.diagnostics;

import org.jspecify.annotations.Nullable;

public class FailureAnalysis {
   private final String description;
   private final @Nullable String action;
   private final Throwable cause;

   public FailureAnalysis(@Nullable String description, @Nullable String action, Throwable cause) {
      this.description = description != null ? description : "";
      this.action = action;
      this.cause = cause;
   }

   public String getDescription() {
      return this.description;
   }

   public @Nullable String getAction() {
      return this.action;
   }

   public Throwable getCause() {
      return this.cause;
   }
}
