package org.springframework.web.server;

import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;

public class ServerErrorException extends ResponseStatusException {
   private final @Nullable Method handlerMethod;
   private final @Nullable MethodParameter parameter;

   public ServerErrorException(String reason, @Nullable Throwable cause) {
      super(HttpStatus.INTERNAL_SERVER_ERROR, reason, cause, null, new Object[]{reason});
      this.handlerMethod = null;
      this.parameter = null;
   }

   public ServerErrorException(String reason, Method handlerMethod, @Nullable Throwable cause) {
      super(HttpStatus.INTERNAL_SERVER_ERROR, reason, cause, null, new Object[]{reason});
      this.handlerMethod = handlerMethod;
      this.parameter = null;
   }

   public ServerErrorException(String reason, MethodParameter parameter, @Nullable Throwable cause) {
      super(HttpStatus.INTERNAL_SERVER_ERROR, reason, cause, null, new Object[]{reason});
      this.handlerMethod = parameter.getMethod();
      this.parameter = parameter;
   }

   public @Nullable Method getHandlerMethod() {
      return this.handlerMethod;
   }

   public @Nullable MethodParameter getMethodParameter() {
      return this.parameter;
   }
}
