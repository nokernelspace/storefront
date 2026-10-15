package org.springframework.aop.interceptor;

import java.io.Serializable;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.util.Assert;

public abstract class AbstractTraceInterceptor implements MethodInterceptor, Serializable {
   protected transient @Nullable Log defaultLogger = LogFactory.getLog(this.getClass());
   private boolean hideProxyClassNames = false;
   private boolean logExceptionStackTrace = true;

   public void setUseDynamicLogger(boolean useDynamicLogger) {
      this.defaultLogger = useDynamicLogger ? null : LogFactory.getLog(this.getClass());
   }

   public void setLoggerName(String loggerName) {
      this.defaultLogger = LogFactory.getLog(loggerName);
   }

   public void setHideProxyClassNames(boolean hideProxyClassNames) {
      this.hideProxyClassNames = hideProxyClassNames;
   }

   public void setLogExceptionStackTrace(boolean logExceptionStackTrace) {
      this.logExceptionStackTrace = logExceptionStackTrace;
   }

   @Override
   public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {
      Log logger = this.getLoggerForInvocation(invocation);
      return this.isInterceptorEnabled(invocation, logger) ? this.invokeUnderTrace(invocation, logger) : invocation.proceed();
   }

   protected Log getLoggerForInvocation(MethodInvocation invocation) {
      if (this.defaultLogger != null) {
         return this.defaultLogger;
      }

      Object target = invocation.getThis();
      Assert.state(target != null, "Target must not be null");
      return LogFactory.getLog(this.getClassForLogging(target));
   }

   protected Class<?> getClassForLogging(Object target) {
      return this.hideProxyClassNames ? AopUtils.getTargetClass(target) : target.getClass();
   }

   protected boolean isInterceptorEnabled(MethodInvocation invocation, Log logger) {
      return this.isLogEnabled(logger);
   }

   protected boolean isLogEnabled(Log logger) {
      return logger.isTraceEnabled();
   }

   protected void writeToLog(Log logger, String message) {
      this.writeToLog(logger, message, null);
   }

   protected void writeToLog(Log logger, String message, @Nullable Throwable ex) {
      if (ex != null && this.logExceptionStackTrace) {
         logger.trace(message, ex);
      } else {
         logger.trace(message);
      }
   }

   protected abstract @Nullable Object invokeUnderTrace(MethodInvocation invocation, Log logger) throws Throwable;
}
