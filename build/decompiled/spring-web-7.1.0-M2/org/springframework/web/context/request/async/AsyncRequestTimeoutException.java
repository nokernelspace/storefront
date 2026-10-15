package org.springframework.web.context.request.async;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;

public class AsyncRequestTimeoutException extends RuntimeException implements ErrorResponse {
   private final ProblemDetail body = ProblemDetail.forStatus(this.getStatusCode());

   @Override
   public HttpStatusCode getStatusCode() {
      return HttpStatus.SERVICE_UNAVAILABLE;
   }

   @Override
   public ProblemDetail getBody() {
      return this.body;
   }
}
