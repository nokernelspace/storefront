package org.springframework.web.server;

import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;

public class ServerWebInputException extends ResponseStatusException {
   private final @Nullable MethodParameter parameter;

   public ServerWebInputException(String reason) {
      this(reason, null, null);
   }

   public ServerWebInputException(String reason, @Nullable MethodParameter parameter) {
      this(reason, parameter, null);
   }

   public ServerWebInputException(String reason, @Nullable MethodParameter parameter, @Nullable Throwable cause) {
      this(reason, parameter, cause, null, null);
   }

   protected ServerWebInputException(
      String reason,
      @Nullable MethodParameter parameter,
      @Nullable Throwable cause,
      @Nullable String messageDetailCode,
      Object @Nullable [] messageDetailArguments
   ) {
      super(HttpStatus.BAD_REQUEST, reason, cause, messageDetailCode, messageDetailArguments);
      this.parameter = parameter;
   }

   public @Nullable MethodParameter getMethodParameter() {
      return this.parameter;
   }
}
