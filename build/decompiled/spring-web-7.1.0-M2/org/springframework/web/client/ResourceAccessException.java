package org.springframework.web.client;

import java.io.IOException;
import org.jspecify.annotations.Nullable;

public class ResourceAccessException extends RestClientException {
   private static final long serialVersionUID = -8513182514355844870L;

   public ResourceAccessException(String msg) {
      super(msg);
   }

   public ResourceAccessException(String msg, @Nullable IOException ex) {
      super(msg, ex);
   }
}
