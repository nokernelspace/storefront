package org.springframework.web;

import jakarta.servlet.ServletException;
import java.util.Collection;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public class HttpRequestMethodNotSupportedException extends ServletException implements ErrorResponse {
   private final String method;
   private final String @Nullable [] supportedMethods;
   private final ProblemDetail body;

   public HttpRequestMethodNotSupportedException(String method) {
      this(method, (String[])null);
   }

   public HttpRequestMethodNotSupportedException(String method, @Nullable Collection<String> supportedMethods) {
      this(method, supportedMethods != null ? StringUtils.toStringArray(supportedMethods) : null);
   }

   private HttpRequestMethodNotSupportedException(String method, String @Nullable [] supportedMethods) {
      super("Request method '" + method + "' is not supported");
      this.method = method;
      this.supportedMethods = supportedMethods;
      String detail = "Method '" + method + "' is not supported.";
      this.body = ProblemDetail.forStatusAndDetail(this.getStatusCode(), detail);
   }

   public String getMethod() {
      return this.method;
   }

   public String @Nullable [] getSupportedMethods() {
      return this.supportedMethods;
   }

   public @Nullable Set<HttpMethod> getSupportedHttpMethods() {
      if (this.supportedMethods == null) {
         return null;
      }

      Set<HttpMethod> supportedMethods = CollectionUtils.newLinkedHashSet(this.supportedMethods.length);

      for (String value : this.supportedMethods) {
         HttpMethod method = HttpMethod.valueOf(value);
         supportedMethods.add(method);
      }

      return supportedMethods;
   }

   @Override
   public HttpStatusCode getStatusCode() {
      return HttpStatus.METHOD_NOT_ALLOWED;
   }

   @Override
   public HttpHeaders getHeaders() {
      if (ObjectUtils.isEmpty(this.supportedMethods)) {
         return HttpHeaders.EMPTY;
      }

      HttpHeaders headers = new HttpHeaders();
      headers.add("Allow", StringUtils.arrayToDelimitedString(this.supportedMethods, ", "));
      return headers;
   }

   @Override
   public ProblemDetail getBody() {
      return this.body;
   }

   @Override
   public Object[] getDetailMessageArguments() {
      return new Object[]{this.getMethod(), this.getSupportedHttpMethods()};
   }
}
