package org.springframework.aop.interceptor;

import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public class SimpleTraceInterceptor extends AbstractTraceInterceptor {
   public SimpleTraceInterceptor() {
   }

   public SimpleTraceInterceptor(boolean useDynamicLogger) {
      this.setUseDynamicLogger(useDynamicLogger);
   }

   @Override
   protected @Nullable Object invokeUnderTrace(MethodInvocation invocation, Log logger) throws Throwable {
      String invocationDescription = this.getInvocationDescription(invocation);
      this.writeToLog(logger, "Entering " + invocationDescription);

      try {
         Object rval = invocation.proceed();
         this.writeToLog(logger, "Exiting " + invocationDescription);
         return rval;
      } catch (Throwable ex) {
         this.writeToLog(logger, "Exception thrown in " + invocationDescription, ex);
         throw ex;
      }
   }

   protected String getInvocationDescription(MethodInvocation invocation) {
      Object target = invocation.getThis();
      Assert.state(target != null, "Target must not be null");
      String className = target.getClass().getName();
      return "method '" + invocation.getMethod().getName() + "' of class [" + className + "]";
   }
}
