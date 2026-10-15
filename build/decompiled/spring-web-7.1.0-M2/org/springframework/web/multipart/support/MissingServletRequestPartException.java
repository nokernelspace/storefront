package org.springframework.web.multipart.support;

import jakarta.servlet.ServletException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;

public class MissingServletRequestPartException extends ServletException implements ErrorResponse {
   private final String requestPartName;
   private final ProblemDetail body = ProblemDetail.forStatus(this.getStatusCode());

   public MissingServletRequestPartException(String requestPartName) {
      super("Required part '" + requestPartName + "' is not present.");
      this.requestPartName = requestPartName;
      this.getBody().setDetail(this.getMessage());
   }

   public String getRequestPartName() {
      return this.requestPartName;
   }

   @Override
   public HttpStatusCode getStatusCode() {
      return HttpStatus.BAD_REQUEST;
   }

   @Override
   public ProblemDetail getBody() {
      return this.body;
   }

   @Override
   public Object[] getDetailMessageArguments() {
      return new Object[]{this.getRequestPartName()};
   }
}
