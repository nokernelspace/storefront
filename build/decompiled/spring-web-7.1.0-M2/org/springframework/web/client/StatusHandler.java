package org.springframework.web.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

final class StatusHandler {
   private final StatusHandler.ResponsePredicate predicate;
   private final RestClient.ResponseSpec.ErrorHandler handler;

   private StatusHandler(StatusHandler.ResponsePredicate predicate, RestClient.ResponseSpec.ErrorHandler handler) {
      this.predicate = predicate;
      this.handler = handler;
   }

   public boolean test(ClientHttpResponse response) throws IOException {
      return this.predicate.test(response);
   }

   public void handle(HttpRequest request, ClientHttpResponse response) throws IOException {
      this.handler.handle(request, response);
   }

   public static StatusHandler of(Predicate<HttpStatusCode> predicate, RestClient.ResponseSpec.ErrorHandler errorHandler) {
      Assert.notNull(predicate, "Predicate must not be null");
      Assert.notNull(errorHandler, "ErrorHandler must not be null");
      return new StatusHandler(response -> predicate.test(response.getStatusCode()), errorHandler);
   }

   @Deprecated(since = "7.1", forRemoval = true)
   public static StatusHandler fromErrorHandler(ResponseErrorHandler errorHandler) {
      Assert.notNull(errorHandler, "ResponseErrorHandler must not be null");
      return new StatusHandler(errorHandler::hasError, (request, response) -> errorHandler.handleError(request.getURI(), request.getMethod(), response));
   }

   public static StatusHandler createDefaultStatusHandler(List<HttpMessageConverter<?>> converters) {
      return new StatusHandler(response -> response.getStatusCode().isError(), (request, response) -> {
         throw createException(response, converters);
      });
   }

   public static RestClientResponseException createException(ClientHttpResponse response, List<HttpMessageConverter<?>> converters) throws IOException {
      HttpStatusCode statusCode = response.getStatusCode();
      String statusText = response.getStatusText();
      HttpHeaders headers = response.getHeaders();
      byte[] body = RestClientUtils.getBody(response);
      Charset charset = RestClientUtils.getCharset(response);
      String message = getErrorMessage(statusCode.value(), statusText, body, charset);
      RestClientResponseException ex;
      if (statusCode.is4xxClientError()) {
         ex = HttpClientErrorException.create(message, statusCode, statusText, headers, body, charset);
      } else if (statusCode.is5xxServerError()) {
         ex = HttpServerErrorException.create(message, statusCode, statusText, headers, body, charset);
      } else {
         ex = new UnknownHttpStatusCodeException(message, statusCode.value(), statusText, headers, body, charset);
      }

      if (!CollectionUtils.isEmpty(converters)) {
         ex.setBodyConvertFunction(initBodyConvertFunction(response, body, converters));
      }

      return ex;
   }

   private static String getErrorMessage(int rawStatusCode, String statusText, byte @Nullable [] responseBody, @Nullable Charset charset) {
      String preface = rawStatusCode + " " + statusText + ": ";
      if (ObjectUtils.isEmpty(responseBody)) {
         return preface + "[no body]";
      }

      charset = charset != null ? charset : StandardCharsets.UTF_8;
      String bodyText = new String(responseBody, charset);
      bodyText = LogFormatUtils.formatValue(bodyText, -1, true);
      return preface + bodyText;
   }

   private static Function<ResolvableType, ? extends @Nullable Object> initBodyConvertFunction(
      ClientHttpResponse response, byte[] body, List<HttpMessageConverter<?>> messageConverters
   ) {
      return resolvableType -> {
         try {
            ClientHttpResponseDecorator responseWrapper = new ClientHttpResponseDecorator(response) {
               @Override
               public InputStream getBody() {
                  return new ByteArrayInputStream(body);
               }
            };
            return RestClientUtils.readWithMessageConverters(
               responseWrapper, messageConverters, resolvableType.getType(), resolvableType.resolve(Object.class), null
            );
         } catch (IOException ex) {
            throw new RestClientException("Error while extracting response for type [" + resolvableType + "]", ex);
         }
      };
   }

   @FunctionalInterface
   private interface ResponsePredicate {
      boolean test(ClientHttpResponse response) throws IOException;
   }
}
