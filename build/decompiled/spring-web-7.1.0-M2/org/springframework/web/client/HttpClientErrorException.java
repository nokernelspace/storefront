package org.springframework.web.client;

import java.nio.charset.Charset;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public class HttpClientErrorException extends HttpStatusCodeException {
   private static final long serialVersionUID = 5177019431887513952L;

   public HttpClientErrorException(HttpStatusCode statusCode) {
      super(statusCode);
   }

   public HttpClientErrorException(HttpStatusCode statusCode, String statusText) {
      super(statusCode, statusText);
   }

   public HttpClientErrorException(HttpStatusCode statusCode, String statusText, byte @Nullable [] body, @Nullable Charset responseCharset) {
      super(statusCode, statusText, body, responseCharset);
   }

   public HttpClientErrorException(
      HttpStatusCode statusCode, String statusText, @Nullable HttpHeaders headers, byte @Nullable [] body, @Nullable Charset responseCharset
   ) {
      super(statusCode, statusText, headers, body, responseCharset);
   }

   public HttpClientErrorException(
      String message, HttpStatusCode statusCode, String statusText, @Nullable HttpHeaders headers, byte @Nullable [] body, @Nullable Charset responseCharset
   ) {
      super(message, statusCode, statusText, headers, body, responseCharset);
   }

   public static HttpClientErrorException create(
      HttpStatusCode statusCode, String statusText, HttpHeaders headers, byte @Nullable [] body, @Nullable Charset charset
   ) {
      return create(null, statusCode, statusText, headers, body, charset);
   }

   public static HttpClientErrorException create(
      @Nullable String message, HttpStatusCode statusCode, String statusText, HttpHeaders headers, byte @Nullable [] body, @Nullable Charset charset
   ) {
      if (statusCode instanceof HttpStatus status) {
         return switch (status) {
            case BAD_REQUEST -> message != null
               ? new HttpClientErrorException.BadRequest(message, statusText, headers, body, charset)
               : new HttpClientErrorException.BadRequest(statusText, headers, body, charset);
            case UNAUTHORIZED -> message != null
               ? new HttpClientErrorException.Unauthorized(message, statusText, headers, body, charset)
               : new HttpClientErrorException.Unauthorized(statusText, headers, body, charset);
            case FORBIDDEN -> message != null
               ? new HttpClientErrorException.Forbidden(message, statusText, headers, body, charset)
               : new HttpClientErrorException.Forbidden(statusText, headers, body, charset);
            case NOT_FOUND -> message != null
               ? new HttpClientErrorException.NotFound(message, statusText, headers, body, charset)
               : new HttpClientErrorException.NotFound(statusText, headers, body, charset);
            case METHOD_NOT_ALLOWED -> message != null
               ? new HttpClientErrorException.MethodNotAllowed(message, statusText, headers, body, charset)
               : new HttpClientErrorException.MethodNotAllowed(statusText, headers, body, charset);
            case NOT_ACCEPTABLE -> message != null
               ? new HttpClientErrorException.NotAcceptable(message, statusText, headers, body, charset)
               : new HttpClientErrorException.NotAcceptable(statusText, headers, body, charset);
            case CONFLICT -> message != null
               ? new HttpClientErrorException.Conflict(message, statusText, headers, body, charset)
               : new HttpClientErrorException.Conflict(statusText, headers, body, charset);
            case GONE -> message != null
               ? new HttpClientErrorException.Gone(message, statusText, headers, body, charset)
               : new HttpClientErrorException.Gone(statusText, headers, body, charset);
            case PRECONDITION_FAILED -> message != null
               ? new HttpClientErrorException.PreconditionFailed(message, statusText, headers, body, charset)
               : new HttpClientErrorException.PreconditionFailed(statusText, headers, body, charset);
            case UNSUPPORTED_MEDIA_TYPE -> message != null
               ? new HttpClientErrorException.UnsupportedMediaType(message, statusText, headers, body, charset)
               : new HttpClientErrorException.UnsupportedMediaType(statusText, headers, body, charset);
            case TOO_MANY_REQUESTS -> message != null
               ? new HttpClientErrorException.TooManyRequests(message, statusText, headers, body, charset)
               : new HttpClientErrorException.TooManyRequests(statusText, headers, body, charset);
            case UNPROCESSABLE_ENTITY -> message != null
               ? new HttpClientErrorException.UnprocessableEntity(message, statusText, headers, body, charset)
               : new HttpClientErrorException.UnprocessableEntity(statusText, headers, body, charset);
            case UNPROCESSABLE_CONTENT -> message != null
               ? new HttpClientErrorException.UnprocessableContent(message, statusText, headers, body, charset)
               : new HttpClientErrorException.UnprocessableContent(statusText, headers, body, charset);
            default -> message != null
               ? new HttpClientErrorException(message, statusCode, statusText, headers, body, charset)
               : new HttpClientErrorException(statusCode, statusText, headers, body, charset);
         };
      } else {
         return message != null
            ? new HttpClientErrorException(message, statusCode, statusText, headers, body, charset)
            : new HttpClientErrorException(statusCode, statusText, headers, body, charset);
      }
   }

   public static final class BadRequest extends HttpClientErrorException {
      private BadRequest(String status, HttpHeaders headers, byte @Nullable [] body, @Nullable Charset charset) {
         super(HttpStatus.BAD_REQUEST, status, headers, body, charset);
      }

      private BadRequest(String message, String status, HttpHeaders headers, byte @Nullable [] body, @Nullable Charset charset) {
         super(message, HttpStatus.BAD_REQUEST, status, headers, body, charset);
      }
   }

   public static final class Conflict extends HttpClientErrorException {
      private Conflict(String status, HttpHeaders headers, byte @Nullable [] body, @Nullable Charset charset) {
         super(HttpStatus.CONFLICT, status, headers, body, charset);
      }

      private Conflict(String message, String status, HttpHeaders headers, byte @Nullable [] body, @Nullable Charset charset) {
         super(message, HttpStatus.CONFLICT, status, headers, body, charset);
      }
   }

   public static final class Forbidden extends HttpClientErrorException {
      private Forbidden(String status, HttpHeaders headers, byte @Nullable [] body, @Nullable Charset charset) {
         super(HttpStatus.FORBIDDEN, status, headers, body, charset);
      }

      private Forbidden(String message, String status, HttpHeaders headers, byte @Nullable [] body, @Nullable Charset charset) {
         super(message, HttpStatus.FORBIDDEN, status, headers, body, charset);
      }
   }

   public static final class Gone extends HttpClientErrorException {
      private Gone(String status, HttpHeaders headers, byte @Nullable [] body, @Nullable Charset charset) {
         super(HttpStatus.GONE, status, headers, body, charset);
      }

      private Gone(String message, String status, HttpHeaders headers, byte @Nullable [] body, @Nullable Charset charset) {
         super(message, HttpStatus.GONE, status, headers, body, charset);
      }
   }

   public static final class MethodNotAllowed extends HttpClientErrorException {
      private MethodNotAllowed(String status, HttpHeaders headers, byte @Nullable [] body, @Nullable Charset charset) {
         super(HttpStatus.METHOD_NOT_ALLOWED, status, headers, body, charset);
      }

      private MethodNotAllowed(String message, String status, HttpHeaders headers, byte @Nullable [] body, @Nullable Charset charset) {
         super(message, HttpStatus.METHOD_NOT_ALLOWED, status, headers, body, charset);
      }
   }

   public static final class NotAcceptable extends HttpClientErrorException {
      private NotAcceptable(String status, HttpHeaders headers, byte @Nullable [] body, @Nullable Charset charset) {
         super(HttpStatus.NOT_ACCEPTABLE, status, headers, body, charset);
      }

      private NotAcceptable(String message, String status, HttpHeaders headers, byte @Nullable [] body, @Nullable Charset charset) {
         super(message, HttpStatus.NOT_ACCEPTABLE, status, headers, body, charset);
      }
   }

   public static final class NotFound extends HttpClientErrorException {
      private NotFound(String status, HttpHeaders headers, byte @Nullable [] body, @Nullable Charset charset) {
         super(HttpStatus.NOT_FOUND, status, headers, body, charset);
      }

      private NotFound(String message, String status, HttpHeaders headers, byte @Nullable [] body, @Nullable Charset charset) {
         super(message, HttpStatus.NOT_FOUND, status, headers, body, charset);
      }
   }

   public static final class PreconditionFailed extends HttpClientErrorException {
      private PreconditionFailed(String status, HttpHeaders headers, byte @Nullable [] body, @Nullable Charset charset) {
         super(HttpStatus.PRECONDITION_FAILED, status, headers, body, charset);
      }

      private PreconditionFailed(String message, String status, HttpHeaders headers, byte @Nullable [] body, @Nullable Charset charset) {
         super(message, HttpStatus.PRECONDITION_FAILED, status, headers, body, charset);
      }
   }

   public static final class TooManyRequests extends HttpClientErrorException {
      private TooManyRequests(String status, HttpHeaders headers, byte @Nullable [] body, @Nullable Charset charset) {
         super(HttpStatus.TOO_MANY_REQUESTS, status, headers, body, charset);
      }

      private TooManyRequests(String message, String status, HttpHeaders headers, byte @Nullable [] body, @Nullable Charset charset) {
         super(message, HttpStatus.TOO_MANY_REQUESTS, status, headers, body, charset);
      }
   }

   public static final class Unauthorized extends HttpClientErrorException {
      private Unauthorized(String status, HttpHeaders headers, byte @Nullable [] body, @Nullable Charset charset) {
         super(HttpStatus.UNAUTHORIZED, status, headers, body, charset);
      }

      private Unauthorized(String message, String status, HttpHeaders headers, byte @Nullable [] body, @Nullable Charset charset) {
         super(message, HttpStatus.UNAUTHORIZED, status, headers, body, charset);
      }
   }

   public static final class UnprocessableContent extends HttpClientErrorException {
      private UnprocessableContent(String status, HttpHeaders headers, byte @Nullable [] body, @Nullable Charset charset) {
         super(HttpStatus.UNPROCESSABLE_CONTENT, status, headers, body, charset);
      }

      private UnprocessableContent(String message, String status, HttpHeaders headers, byte @Nullable [] body, @Nullable Charset charset) {
         super(message, HttpStatus.UNPROCESSABLE_CONTENT, status, headers, body, charset);
      }
   }

   @Deprecated(since = "7.0")
   public static final class UnprocessableEntity extends HttpClientErrorException {
      private UnprocessableEntity(String status, HttpHeaders headers, byte @Nullable [] body, @Nullable Charset charset) {
         super(HttpStatus.UNPROCESSABLE_ENTITY, status, headers, body, charset);
      }

      private UnprocessableEntity(String message, String status, HttpHeaders headers, byte @Nullable [] body, @Nullable Charset charset) {
         super(message, HttpStatus.UNPROCESSABLE_ENTITY, status, headers, body, charset);
      }
   }

   public static final class UnsupportedMediaType extends HttpClientErrorException {
      private UnsupportedMediaType(String status, HttpHeaders headers, byte @Nullable [] body, @Nullable Charset charset) {
         super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, status, headers, body, charset);
      }

      private UnsupportedMediaType(String message, String status, HttpHeaders headers, byte @Nullable [] body, @Nullable Charset charset) {
         super(message, HttpStatus.UNSUPPORTED_MEDIA_TYPE, status, headers, body, charset);
      }
   }
}
