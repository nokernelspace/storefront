package org.springframework.web;

import java.net.URI;
import java.util.Locale;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

public interface ErrorResponse {
   HttpStatusCode getStatusCode();

   default HttpHeaders getHeaders() {
      return HttpHeaders.EMPTY;
   }

   ProblemDetail getBody();

   default String getTypeMessageCode() {
      return getDefaultTypeMessageCode(this.getClass());
   }

   default String getTitleMessageCode() {
      return getDefaultTitleMessageCode(this.getClass());
   }

   default String getDetailMessageCode() {
      return getDefaultDetailMessageCode(this.getClass(), null);
   }

   default Object @Nullable [] getDetailMessageArguments() {
      return null;
   }

   default Object @Nullable [] getDetailMessageArguments(MessageSource messageSource, Locale locale) {
      return this.getDetailMessageArguments();
   }

   default ProblemDetail updateAndGetBody(@Nullable MessageSource messageSource, Locale locale) {
      if (messageSource != null) {
         String type = messageSource.getMessage(this.getTypeMessageCode(), null, null, locale);
         if (type != null) {
            this.getBody().setType(URI.create(type));
         }

         Object[] arguments = this.getDetailMessageArguments(messageSource, locale);
         String detail = messageSource.getMessage(this.getDetailMessageCode(), arguments, null, locale);
         if (detail != null) {
            this.getBody().setDetail(detail);
         }

         String title = messageSource.getMessage(this.getTitleMessageCode(), null, null, locale);
         if (title != null) {
            this.getBody().setTitle(title);
         }
      }

      return this.getBody();
   }

   static String getDefaultTypeMessageCode(Class<?> exceptionType) {
      return "problemDetail.type." + exceptionType.getName();
   }

   static String getDefaultTitleMessageCode(Class<?> exceptionType) {
      return "problemDetail.title." + exceptionType.getName();
   }

   static String getDefaultDetailMessageCode(Class<?> exceptionType, @Nullable String suffix) {
      return "problemDetail." + exceptionType.getName() + (suffix != null ? "." + suffix : "");
   }

   static ErrorResponse create(Throwable ex, HttpStatusCode statusCode, String detail) {
      return builder(ex, statusCode, detail).build();
   }

   static ErrorResponse.Builder builder(Throwable ex, HttpStatusCode statusCode, String detail) {
      return builder(ex, ProblemDetail.forStatusAndDetail(statusCode, detail));
   }

   static ErrorResponse.Builder builder(Throwable ex, ProblemDetail problemDetail) {
      return new DefaultErrorResponseBuilder(ex, problemDetail);
   }

   interface Builder {
      ErrorResponse.Builder header(String headerName, String... headerValues);

      ErrorResponse.Builder headers(Consumer<HttpHeaders> headersConsumer);

      ErrorResponse.Builder type(URI type);

      ErrorResponse.Builder typeMessageCode(String messageCode);

      ErrorResponse.Builder title(@Nullable String title);

      ErrorResponse.Builder titleMessageCode(String messageCode);

      ErrorResponse.Builder instance(@Nullable URI instance);

      ErrorResponse.Builder detail(String detail);

      ErrorResponse.Builder detailMessageCode(String messageCode);

      ErrorResponse.Builder detailMessageArguments(Object... messageArguments);

      ErrorResponse.Builder property(String name, @Nullable Object value);

      ErrorResponse build();

      default ErrorResponse build(@Nullable MessageSource messageSource, Locale locale) {
         ErrorResponse response = this.build();
         response.updateAndGetBody(messageSource, locale);
         return response;
      }
   }

   interface Interceptor {
      void handleError(ProblemDetail detail, @Nullable ErrorResponse errorResponse);
   }
}
