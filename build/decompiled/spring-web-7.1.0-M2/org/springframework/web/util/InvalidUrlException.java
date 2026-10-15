package org.springframework.web.util;

public class InvalidUrlException extends IllegalArgumentException {
   private static final long serialVersionUID = 7409308391039105562L;

   public InvalidUrlException(String msg) {
      super(msg);
   }

   public InvalidUrlException(String msg, Throwable cause) {
      super(msg, cause);
   }
}
