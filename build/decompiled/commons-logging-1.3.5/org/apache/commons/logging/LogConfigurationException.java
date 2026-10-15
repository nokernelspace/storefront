package org.apache.commons.logging;

import java.util.Objects;

public class LogConfigurationException extends RuntimeException {
   private static final long serialVersionUID = 8486587136871052495L;
   @Deprecated
   protected Throwable cause;

   public LogConfigurationException() {
   }

   public LogConfigurationException(String message) {
      super(message);
   }

   public LogConfigurationException(String message, Throwable cause) {
      super(message, cause);
      this.cause = cause;
   }

   public LogConfigurationException(Throwable cause) {
      this(Objects.toString(cause, null), cause);
   }
}
