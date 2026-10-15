package org.springframework.core.type.classreading;

import java.io.IOException;

public class ClassFormatException extends IOException {
   public ClassFormatException(String message) {
      super(message);
   }

   public ClassFormatException(String message, Throwable cause) {
      super(message, cause);
   }
}
