package org.springframework.aop.interceptor;

import java.lang.reflect.Method;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

public class SimpleAsyncUncaughtExceptionHandler implements AsyncUncaughtExceptionHandler {
   private static final Log logger = LogFactory.getLog(SimpleAsyncUncaughtExceptionHandler.class);

   @Override
   public void handleUncaughtException(Throwable ex, Method method, @Nullable Object... params) {
      if (logger.isErrorEnabled()) {
         logger.error("Unexpected exception occurred invoking async method: " + method, ex);
      }
   }
}
