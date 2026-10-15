package org.springframework.web.bind;

import jakarta.servlet.ServletException;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;

public class ServletRequestBindingException extends ServletException implements ErrorResponse {
   private final String messageDetailCode;
   private final Object @Nullable [] messageDetailArguments;
   private @Nullable ProblemDetail body;

   public ServletRequestBindingException(@Nullable String msg) {
      this(msg, null, null);
   }

   public ServletRequestBindingException(@Nullable String msg, @Nullable Throwable cause) {
      this(msg, cause, null, null);
   }

   protected ServletRequestBindingException(@Nullable String msg, @Nullable String messageDetailCode, Object @Nullable [] messageDetailArguments) {
      this(msg, null, messageDetailCode, messageDetailArguments);
   }

   protected ServletRequestBindingException(
      @Nullable String msg, @Nullable Throwable cause, @Nullable String messageDetailCode, Object @Nullable [] messageDetailArguments
   ) {
      super(msg, cause);
      this.messageDetailCode = this.initMessageDetailCode(messageDetailCode);
      this.messageDetailArguments = messageDetailArguments;
   }

   private String initMessageDetailCode(@Nullable String messageDetailCode) {
      return messageDetailCode != null ? messageDetailCode : ErrorResponse.getDefaultDetailMessageCode(this.getClass(), null);
   }

   @Override
   public HttpStatusCode getStatusCode() {
      return HttpStatus.BAD_REQUEST;
   }

   @Override
   public synchronized ProblemDetail getBody() {
      if (this.body == null) {
         this.body = ProblemDetail.forStatus(this.getStatusCode());
      }

      return this.body;
   }

   @Override
   public String getDetailMessageCode() {
      return this.messageDetailCode;
   }

   @Override
   public Object @Nullable [] getDetailMessageArguments() {
      return this.messageDetailArguments;
   }
}
