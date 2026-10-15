package org.springframework.web.client;

import org.jspecify.annotations.Nullable;
import org.springframework.core.NestedRuntimeException;

public class RestClientException extends NestedRuntimeException {
   private static final long serialVersionUID = -4084444984163796577L;

   public RestClientException(String msg) {
      super(msg);
   }

   public RestClientException(String msg, @Nullable Throwable ex) {
      super(msg, ex);
   }
}
