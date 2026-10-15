package org.springframework.web.multipart;

import org.jspecify.annotations.Nullable;
import org.springframework.core.NestedRuntimeException;

public class MultipartException extends NestedRuntimeException {
   public MultipartException(String msg) {
      super(msg);
   }

   public MultipartException(String msg, @Nullable Throwable cause) {
      super(msg, cause);
   }
}
