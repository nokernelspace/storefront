package org.springframework.resilience.retry;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.context.event.MethodFailureEvent;
import org.springframework.util.ClassUtils;

public class MethodRetryEvent extends MethodFailureEvent {
   private final boolean retryAborted;

   public MethodRetryEvent(MethodInvocation invocation, Throwable failure, boolean retryAborted) {
      super(invocation, failure);
      this.retryAborted = retryAborted;
   }

   @Override
   public Throwable getFailure() {
      return super.getFailure();
   }

   public boolean isRetryAborted() {
      return this.retryAborted;
   }

   @Override
   public String toString() {
      return "MethodRetryEvent: " + ClassUtils.getQualifiedMethodName(this.getMethod()) + " [" + this.getFailure() + "]";
   }
}
