package org.springframework.core.io.buffer;

public class DataBufferLimitException extends IllegalStateException {
   public DataBufferLimitException(String message) {
      super(message);
   }

   public DataBufferLimitException(String message, Throwable cause) {
      super(message, cause);
   }
}
