package org.springframework.web.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

@Deprecated(since = "7.1", forRemoval = true)
public class DefaultResponseErrorHandler implements ResponseErrorHandler {
   private @Nullable List<HttpMessageConverter<?>> messageConverters;

   protected void setMessageConverters(List<HttpMessageConverter<?>> converters) {
      this.messageConverters = Collections.unmodifiableList(converters);
   }

   @Override
   public boolean hasError(ClientHttpResponse response) throws IOException {
      HttpStatusCode statusCode = response.getStatusCode();
      return this.hasError(statusCode);
   }

   protected boolean hasError(HttpStatusCode statusCode) {
      return statusCode.isError();
   }

   @Override
   public void handleError(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
      this.handleError(response, response.getStatusCode(), url, method);
   }

   protected void handleError(ClientHttpResponse response, HttpStatusCode statusCode, @Nullable URI url, @Nullable HttpMethod method) throws IOException {
      String statusText = response.getStatusText();
      HttpHeaders headers = response.getHeaders();
      byte[] body = this.getResponseBody(response);
      Charset charset = this.getCharset(response);
      String message = this.getErrorMessage(statusCode.value(), statusText, body, charset, url, method);
      RestClientResponseException ex;
      if (statusCode.is4xxClientError()) {
         ex = HttpClientErrorException.create(message, statusCode, statusText, headers, body, charset);
      } else if (statusCode.is5xxServerError()) {
         ex = HttpServerErrorException.create(message, statusCode, statusText, headers, body, charset);
      } else {
         ex = new UnknownHttpStatusCodeException(message, statusCode.value(), statusText, headers, body, charset);
      }

      if (!CollectionUtils.isEmpty(this.messageConverters)) {
         ex.setBodyConvertFunction((Function<ResolvableType, ? extends Object>)this.initBodyConvertFunction(response, body));
      }

      throw ex;
   }

   protected byte[] getResponseBody(ClientHttpResponse response) {
      return RestClientUtils.getBody(response);
   }

   protected @Nullable Charset getCharset(ClientHttpResponse response) {
      MediaType contentType = response.getHeaders().getContentType();
      return contentType != null ? contentType.getCharset() : null;
   }

   private String getErrorMessage(
      int rawStatusCode, String statusText, byte @Nullable [] responseBody, @Nullable Charset charset, @Nullable URI url, @Nullable HttpMethod method
   ) {
      StringBuilder msg = new StringBuilder(rawStatusCode + " " + statusText);
      if (method != null) {
         msg.append(" on ").append(method).append(" request");
      }

      if (url != null) {
         msg.append(" for \"");
         String urlString = url.toString();
         int idx = urlString.indexOf(63);
         if (idx != -1) {
            msg.append(urlString, 0, idx);
         } else {
            msg.append(urlString);
         }

         msg.append("\"");
      }

      msg.append(": ");
      if (ObjectUtils.isEmpty(responseBody)) {
         msg.append("[no body]");
      } else {
         charset = charset != null ? charset : StandardCharsets.UTF_8;
         String bodyText = new String(responseBody, charset);
         bodyText = LogFormatUtils.formatValue(bodyText, -1, true);
         msg.append(bodyText);
      }

      return msg.toString();
   }

   protected Function<ResolvableType, ?> initBodyConvertFunction(ClientHttpResponse response, byte[] body) {
      Assert.state(!CollectionUtils.isEmpty(this.messageConverters), "Expected message converters");
      return resolvableType -> {
         try {
            HttpMessageConverterExtractor<?> extractor = new HttpMessageConverterExtractor(resolvableType.getType(), this.messageConverters);
            return extractor.extractData(new ClientHttpResponseDecorator(response) {
               @Override
               public InputStream getBody() {
                  return new ByteArrayInputStream(body);
               }
            });
         } catch (IOException ex) {
            throw new RestClientException("Error while extracting response for type [" + resolvableType + "]", ex);
         }
      };
   }
}
