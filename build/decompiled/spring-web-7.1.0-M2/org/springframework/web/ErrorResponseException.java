package org.springframework.web;

import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.core.NestedRuntimeException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

public class ErrorResponseException extends NestedRuntimeException implements ErrorResponse {
   private final HttpStatusCode status;
   private final HttpHeaders headers = new HttpHeaders();
   private final ProblemDetail body;
   private final String messageDetailCode;
   private final Object @Nullable [] messageDetailArguments;

   public ErrorResponseException(HttpStatusCode status) {
      this(status, null);
   }

   public ErrorResponseException(HttpStatusCode status, @Nullable Throwable cause) {
      this(status, ProblemDetail.forStatus(status), cause);
   }

   public ErrorResponseException(HttpStatusCode status, ProblemDetail body, @Nullable Throwable cause) {
      this(status, body, cause, null, null);
   }

   public ErrorResponseException(
      HttpStatusCode status, ProblemDetail body, @Nullable Throwable cause, @Nullable String messageDetailCode, Object @Nullable [] messageDetailArguments
   ) {
      super(null, cause);
      this.status = status;
      this.body = body;
      this.messageDetailCode = this.initMessageDetailCode(messageDetailCode);
      this.messageDetailArguments = messageDetailArguments;
   }

   private String initMessageDetailCode(@Nullable String messageDetailCode) {
      return messageDetailCode != null ? messageDetailCode : ErrorResponse.getDefaultDetailMessageCode(this.getClass(), null);
   }

   @Override
   public HttpStatusCode getStatusCode() {
      return this.status;
   }

   @Override
   public HttpHeaders getHeaders() {
      return this.headers;
   }

   public void setType(@Nullable URI type) {
      this.body.setType(type);
   }

   public void setTitle(@Nullable String title) {
      this.body.setTitle(title);
   }

   public void setDetail(@Nullable String detail) {
      this.body.setDetail(detail);
   }

   public void setInstance(@Nullable URI instance) {
      this.body.setInstance(instance);
   }

   @Override
   public final ProblemDetail getBody() {
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

   public String getMessage() {
      return this.status + (!this.headers.isEmpty() ? ", headers=" + this.headers : "") + ", " + this.body;
   }
}
