package org.springframework.web.client;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.util.CollectionUtils;

@Deprecated(since = "7.1", forRemoval = true)
public class ExtractingResponseErrorHandler extends DefaultResponseErrorHandler {
   private List<HttpMessageConverter<?>> messageConverters = Collections.emptyList();
   private final Map<HttpStatusCode, Class<? extends RestClientException>> statusMapping = new LinkedHashMap<>();
   private final Map<HttpStatus.Series, Class<? extends RestClientException>> seriesMapping = new LinkedHashMap<>();

   public ExtractingResponseErrorHandler() {
   }

   public ExtractingResponseErrorHandler(List<HttpMessageConverter<?>> messageConverters) {
      this.messageConverters = messageConverters;
   }

   @Override
   public void setMessageConverters(List<HttpMessageConverter<?>> messageConverters) {
      this.messageConverters = messageConverters;
   }

   public void setStatusMapping(Map<HttpStatusCode, Class<? extends RestClientException>> statusMapping) {
      if (!CollectionUtils.isEmpty(statusMapping)) {
         this.statusMapping.putAll(statusMapping);
      }
   }

   public void setSeriesMapping(Map<HttpStatus.Series, Class<? extends RestClientException>> seriesMapping) {
      if (!CollectionUtils.isEmpty(seriesMapping)) {
         this.seriesMapping.putAll(seriesMapping);
      }
   }

   @Override
   protected boolean hasError(HttpStatusCode statusCode) {
      if (this.statusMapping.containsKey(statusCode)) {
         return this.statusMapping.get(statusCode) != null;
      }

      HttpStatus.Series series = HttpStatus.Series.resolve(statusCode.value());
      return this.seriesMapping.containsKey(series) ? this.seriesMapping.get(series) != null : super.hasError(statusCode);
   }

   @Override
   protected void handleError(ClientHttpResponse response, HttpStatusCode statusCode, @Nullable URI url, @Nullable HttpMethod method) throws IOException {
      if (this.statusMapping.containsKey(statusCode)) {
         this.extract(this.statusMapping.get(statusCode), response);
      }

      HttpStatus.Series series = HttpStatus.Series.resolve(statusCode.value());
      if (this.seriesMapping.containsKey(series)) {
         this.extract(this.seriesMapping.get(series), response);
      } else {
         super.handleError(response, statusCode, url, method);
      }
   }

   private void extract(@Nullable Class<? extends RestClientException> exceptionClass, ClientHttpResponse response) throws IOException {
      if (exceptionClass != null) {
         HttpMessageConverterExtractor<? extends RestClientException> extractor = new HttpMessageConverterExtractor<>(exceptionClass, this.messageConverters);
         RestClientException exception = extractor.extractData(response);
         if (exception != null) {
            throw exception;
         }
      }
   }
}
