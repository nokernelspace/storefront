package org.springframework.scheduling.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ErrorHandler;
import org.springframework.util.ReflectionUtils;

public abstract class TaskUtils {
   public static final ErrorHandler LOG_AND_SUPPRESS_ERROR_HANDLER = new TaskUtils.LoggingErrorHandler();
   public static final ErrorHandler LOG_AND_PROPAGATE_ERROR_HANDLER = new TaskUtils.PropagatingErrorHandler();

   public static DelegatingErrorHandlingRunnable decorateTaskWithErrorHandler(Runnable task, @Nullable ErrorHandler errorHandler, boolean isRepeatingTask) {
      if (task instanceof DelegatingErrorHandlingRunnable dehRunnable) {
         return dehRunnable;
      } else {
         ErrorHandler eh = errorHandler != null ? errorHandler : getDefaultErrorHandler(isRepeatingTask);
         return new DelegatingErrorHandlingRunnable(task, eh);
      }
   }

   public static ErrorHandler getDefaultErrorHandler(boolean isRepeatingTask) {
      return isRepeatingTask ? LOG_AND_SUPPRESS_ERROR_HANDLER : LOG_AND_PROPAGATE_ERROR_HANDLER;
   }

   private static class LoggingErrorHandler implements ErrorHandler {
      private final Log logger = LogFactory.getLog(TaskUtils.LoggingErrorHandler.class);

      public void handleError(Throwable t) {
         this.logger.error("Unexpected error occurred in scheduled task", t);
      }
   }

   private static class PropagatingErrorHandler extends TaskUtils.LoggingErrorHandler {
      @Override
      public void handleError(Throwable t) {
         super.handleError(t);
         ReflectionUtils.rethrowRuntimeException(t);
      }
   }
}
