package org.springframework.resilience;

import java.util.concurrent.RejectedExecutionException;

public class InvocationRejectedException extends RejectedExecutionException {
   private final Object target;

   public InvocationRejectedException(String msg, Object target) {
      super(msg);
      this.target = target;
   }

   public Object getTarget() {
      return this.target;
   }
}
