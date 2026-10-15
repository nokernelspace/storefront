package org.springframework.web.client;

import java.nio.charset.Charset;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public class HttpServerErrorException extends HttpStatusCodeException {
   private static final long serialVersionUID = -2915754006618138282L;

   public HttpServerErrorException(HttpStatusCode statusCode) {
      super(statusCode);
   }

   public HttpServerErrorException(HttpStatusCode statusCode, String statusText) {
      super(statusCode, statusText);
   }

   public HttpServerErrorException(HttpStatusCode statusCode, String statusText, byte @Nullable [] body, @Nullable Charset charset) {
      super(statusCode, statusText, body, charset);
   }

   public HttpServerErrorException(
      HttpStatusCode statusCode, String statusText, @Nullable HttpHeaders headers, byte @Nullable [] body, @Nullable Charset charset
   ) {
      super(statusCode, statusText, headers, body, charset);
   }

   public HttpServerErrorException(
      String message, HttpStatusCode statusCode, String statusText, @Nullable HttpHeaders headers, byte @Nullable [] body, @Nullable Charset charset
   ) {
      super(message, statusCode, statusText, headers, body, charset);
   }

   public static HttpServerErrorException create(HttpStatusCode statusCode, String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset) {
      return create(null, statusCode, statusText, headers, body, charset);
   }

   public static HttpServerErrorException create(
      @Nullable String message, HttpStatusCode statusCode, String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset
   ) {
      if (statusCode instanceof HttpStatus status) {
         return switch (status) {
            case INTERNAL_SERVER_ERROR -> message != null
               ? new HttpServerErrorException.InternalServerError(message, statusText, headers, body, charset)
               : new HttpServerErrorException.InternalServerError(statusText, headers, body, charset);
            case NOT_IMPLEMENTED -> message != null
               ? new HttpServerErrorException.NotImplemented(message, statusText, headers, body, charset)
               : new HttpServerErrorException.NotImplemented(statusText, headers, body, charset);
            case BAD_GATEWAY -> message != null
               ? new HttpServerErrorException.BadGateway(message, statusText, headers, body, charset)
               : new HttpServerErrorException.BadGateway(statusText, headers, body, charset);
            case SERVICE_UNAVAILABLE -> message != null
               ? new HttpServerErrorException.ServiceUnavailable(message, statusText, headers, body, charset)
               : new HttpServerErrorException.ServiceUnavailable(statusText, headers, body, charset);
            case GATEWAY_TIMEOUT -> message != null
               ? new HttpServerErrorException.GatewayTimeout(message, statusText, headers, body, charset)
               : new HttpServerErrorException.GatewayTimeout(statusText, headers, body, charset);
            default -> message != null
               ? new HttpServerErrorException(message, statusCode, statusText, headers, body, charset)
               : new HttpServerErrorException(statusCode, statusText, headers, body, charset);
         };
      } else {
         return message != null
            ? new HttpServerErrorException(message, statusCode, statusText, headers, body, charset)
            : new HttpServerErrorException(statusCode, statusText, headers, body, charset);
      }
   }

   public static final class BadGateway extends HttpServerErrorException {
      private BadGateway(String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset) {
         super(HttpStatus.BAD_GATEWAY, statusText, headers, body, charset);
      }

      private BadGateway(String message, String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset) {
         super(message, HttpStatus.BAD_GATEWAY, statusText, headers, body, charset);
      }
   }

   public static final class GatewayTimeout extends HttpServerErrorException {
      private GatewayTimeout(String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset) {
         super(HttpStatus.GATEWAY_TIMEOUT, statusText, headers, body, charset);
      }

      private GatewayTimeout(String message, String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset) {
         super(message, HttpStatus.GATEWAY_TIMEOUT, statusText, headers, body, charset);
      }
   }

   public static final class InternalServerError extends HttpServerErrorException {
      private InternalServerError(String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset) {
         super(HttpStatus.INTERNAL_SERVER_ERROR, statusText, headers, body, charset);
      }

      private InternalServerError(String message, String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset) {
         super(message, HttpStatus.INTERNAL_SERVER_ERROR, statusText, headers, body, charset);
      }
   }

   public static final class NotImplemented extends HttpServerErrorException {
      private NotImplemented(String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset) {
         super(HttpStatus.NOT_IMPLEMENTED, statusText, headers, body, charset);
      }

      private NotImplemented(String message, String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset) {
         super(message, HttpStatus.NOT_IMPLEMENTED, statusText, headers, body, charset);
      }
   }

   public static final class ServiceUnavailable extends HttpServerErrorException {
      private ServiceUnavailable(String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset) {
         super(HttpStatus.SERVICE_UNAVAILABLE, statusText, headers, body, charset);
      }

      private ServiceUnavailable(String message, String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset) {
         super(message, HttpStatus.SERVICE_UNAVAILABLE, statusText, headers, body, charset);
      }
   }
}
