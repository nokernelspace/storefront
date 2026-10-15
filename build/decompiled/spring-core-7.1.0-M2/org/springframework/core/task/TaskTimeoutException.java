package org.springframework.core.task;

@Deprecated(since = "5.3.16")
public class TaskTimeoutException extends TaskRejectedException {
   public TaskTimeoutException(String msg) {
      super(msg);
   }

   public TaskTimeoutException(String msg, Throwable cause) {
      super(msg, cause);
   }
}
