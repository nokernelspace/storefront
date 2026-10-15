package org.springframework.context.event;

import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.context.ApplicationEvent;
import org.springframework.util.ClassUtils;

public class MethodFailureEvent extends ApplicationEvent {
   private final Throwable failure;

   public MethodFailureEvent(MethodInvocation invocation, Throwable failure) {
      super(invocation);
      this.failure = failure;
   }

   public MethodInvocation getSource() {
      return (MethodInvocation)super.getSource();
   }

   public Method getMethod() {
      return this.getSource().getMethod();
   }

   public Throwable getFailure() {
      return this.failure;
   }

   @Override
   public String toString() {
      return this.getClass().getSimpleName() + ": " + ClassUtils.getQualifiedMethodName(this.getMethod()) + " [" + this.getFailure() + "]";
   }
}
