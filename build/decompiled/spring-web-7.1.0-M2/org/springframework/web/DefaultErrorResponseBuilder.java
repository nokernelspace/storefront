package org.springframework.web;

import java.net.URI;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.util.Assert;

final class DefaultErrorResponseBuilder implements ErrorResponse.Builder {
   private final Throwable exception;
   private final HttpStatusCode statusCode;
   private @Nullable HttpHeaders headers;
   private final ProblemDetail problemDetail;
   private String typeMessageCode;
   private String titleMessageCode;
   private String detailMessageCode;
   private Object @Nullable [] detailMessageArguments;

   DefaultErrorResponseBuilder(Throwable ex, ProblemDetail problemDetail) {
      Assert.notNull(ex, "Throwable is required");
      this.exception = ex;
      this.statusCode = HttpStatusCode.valueOf(problemDetail.getStatus());
      this.problemDetail = problemDetail;
      this.typeMessageCode = ErrorResponse.getDefaultTypeMessageCode(ex.getClass());
      this.titleMessageCode = ErrorResponse.getDefaultTitleMessageCode(ex.getClass());
      this.detailMessageCode = ErrorResponse.getDefaultDetailMessageCode(ex.getClass(), null);
   }

   @Override
   public ErrorResponse.Builder header(String headerName, String... headerValues) {
      for (String headerValue : headerValues) {
         this.getHeaders().add(headerName, headerValue);
      }

      return this;
   }

   @Override
   public ErrorResponse.Builder headers(Consumer<HttpHeaders> headersConsumer) {
      headersConsumer.accept(this.getHeaders());
      return this;
   }

   private HttpHeaders getHeaders() {
      if (this.headers == null) {
         this.headers = new HttpHeaders();
      }

      return this.headers;
   }

   @Override
   public ErrorResponse.Builder type(@Nullable URI type) {
      this.problemDetail.setType(type);
      return this;
   }

   @Override
   public ErrorResponse.Builder typeMessageCode(String messageCode) {
      this.typeMessageCode = messageCode;
      return this;
   }

   @Override
   public ErrorResponse.Builder title(@Nullable String title) {
      this.problemDetail.setTitle(title);
      return this;
   }

   @Override
   public ErrorResponse.Builder titleMessageCode(String messageCode) {
      Assert.notNull(messageCode, "`titleMessageCode` is required");
      this.titleMessageCode = messageCode;
      return this;
   }

   @Override
   public ErrorResponse.Builder instance(@Nullable URI instance) {
      this.problemDetail.setInstance(instance);
      return this;
   }

   @Override
   public ErrorResponse.Builder detail(String detail) {
      this.problemDetail.setDetail(detail);
      return this;
   }

   @Override
   public ErrorResponse.Builder detailMessageCode(String messageCode) {
      Assert.notNull(messageCode, "`detailMessageCode` is required");
      this.detailMessageCode = messageCode;
      return this;
   }

   @Override
   public ErrorResponse.Builder detailMessageArguments(Object... messageArguments) {
      this.detailMessageArguments = messageArguments;
      return this;
   }

   @Override
   public ErrorResponse.Builder property(String name, @Nullable Object value) {
      this.problemDetail.setProperty(name, value);
      return this;
   }

   @Override
   public ErrorResponse build() {
      return new DefaultErrorResponseBuilder.SimpleErrorResponse(
         this.exception,
         this.statusCode,
         this.headers,
         this.problemDetail,
         this.typeMessageCode,
         this.titleMessageCode,
         this.detailMessageCode,
         this.detailMessageArguments
      );
   }

   private static class SimpleErrorResponse implements ErrorResponse {
      private final Throwable exception;
      private final HttpStatusCode statusCode;
      private final HttpHeaders headers;
      private final ProblemDetail problemDetail;
      private final String typeMessageCode;
      private final String titleMessageCode;
      private final String detailMessageCode;
      private final Object @Nullable [] detailMessageArguments;

      SimpleErrorResponse(
         Throwable ex,
         HttpStatusCode statusCode,
         @Nullable HttpHeaders headers,
         ProblemDetail problemDetail,
         String typeMessageCode,
         String titleMessageCode,
         String detailMessageCode,
         Object @Nullable [] detailMessageArguments
      ) {
         this.exception = ex;
         this.statusCode = statusCode;
         this.headers = headers != null ? headers : HttpHeaders.EMPTY;
         this.problemDetail = problemDetail;
         this.typeMessageCode = typeMessageCode;
         this.titleMessageCode = titleMessageCode;
         this.detailMessageCode = detailMessageCode;
         this.detailMessageArguments = detailMessageArguments;
      }

      @Override
      public HttpStatusCode getStatusCode() {
         return this.statusCode;
      }

      @Override
      public HttpHeaders getHeaders() {
         return this.headers;
      }

      @Override
      public ProblemDetail getBody() {
         return this.problemDetail;
      }

      @Override
      public String getTypeMessageCode() {
         return this.typeMessageCode;
      }

      @Override
      public String getTitleMessageCode() {
         return this.titleMessageCode;
      }

      @Override
      public String getDetailMessageCode() {
         return this.detailMessageCode;
      }

      @Override
      public Object @Nullable [] getDetailMessageArguments() {
         return this.detailMessageArguments;
      }

      @Override
      public String toString() {
         return "ErrorResponse{status="
            + this.statusCode
            + ", headers="
            + this.headers
            + ", body="
            + this.problemDetail
            + ", exception="
            + this.exception
            + "}";
      }
   }
}
