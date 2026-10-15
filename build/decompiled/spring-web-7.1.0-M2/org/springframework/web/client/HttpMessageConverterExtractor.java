package org.springframework.web.client;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.SmartHttpMessageConverter;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;

@Deprecated(since = "7.1", forRemoval = true)
public class HttpMessageConverterExtractor<T> implements ResponseExtractor<T> {
   private final Type responseType;
   private final @Nullable Class<T> responseClass;
   private final List<HttpMessageConverter<?>> messageConverters;
   private final Log logger;

   public HttpMessageConverterExtractor(Class<T> responseType, List<HttpMessageConverter<?>> messageConverters) {
      this((Type)responseType, messageConverters);
   }

   public HttpMessageConverterExtractor(Type responseType, List<HttpMessageConverter<?>> messageConverters) {
      this(responseType, messageConverters, LogFactory.getLog(HttpMessageConverterExtractor.class));
   }

   HttpMessageConverterExtractor(Type responseType, List<HttpMessageConverter<?>> messageConverters, Log logger) {
      Assert.notNull(responseType, "'responseType' must not be null");
      Assert.notEmpty(messageConverters, "'messageConverters' must not be empty");
      Assert.noNullElements(messageConverters, "'messageConverters' must not contain null elements");
      this.responseType = responseType;
      this.responseClass = responseType instanceof Class clazz ? clazz : null;
      this.messageConverters = messageConverters;
      this.logger = logger;
   }

   @Override
   public @Nullable T extractData(ClientHttpResponse response) throws IOException {
      IntrospectingClientHttpResponse responseWrapper = new IntrospectingClientHttpResponse(response);
      if (responseWrapper.hasMessageBody() && !responseWrapper.hasEmptyMessageBody()) {
         MediaType contentType = this.getContentType(responseWrapper);

         try {
            for (HttpMessageConverter<?> messageConverter : this.messageConverters) {
               if (messageConverter instanceof GenericHttpMessageConverter genericMessageConverter) {
                  if (genericMessageConverter.canRead(this.responseType, null, contentType)) {
                     if (this.logger.isDebugEnabled()) {
                        ResolvableType resolvableType = ResolvableType.forType(this.responseType);
                        this.logger.debug("Reading to [" + resolvableType + "]");
                     }

                     return (T)genericMessageConverter.read(this.responseType, null, responseWrapper);
                  }
               } else if (messageConverter instanceof SmartHttpMessageConverter smartMessageConverter) {
                  ResolvableType resolvableType = ResolvableType.forType(this.responseType);
                  if (smartMessageConverter.canRead(resolvableType, contentType)) {
                     if (this.logger.isDebugEnabled()) {
                        this.logger.debug("Reading to [" + resolvableType + "]");
                     }

                     return (T)smartMessageConverter.read(resolvableType, responseWrapper, null);
                  }
               } else if (this.responseClass != null && messageConverter.canRead(this.responseClass, contentType)) {
                  if (this.logger.isDebugEnabled()) {
                     String className = this.responseClass.getName();
                     this.logger.debug("Reading to [" + className + "] as \"" + contentType + "\"");
                  }

                  return (T)messageConverter.read(this.responseClass, responseWrapper);
               }
            }
         } catch (IOException | HttpMessageNotReadableException ex) {
            throw new RestClientException("Error while extracting response for type [" + this.responseType + "] and content type [" + contentType + "]", ex);
         }

         throw new UnknownContentTypeException(
            this.responseType,
            contentType,
            responseWrapper.getStatusCode(),
            responseWrapper.getStatusText(),
            responseWrapper.getHeaders(),
            getResponseBody(responseWrapper)
         );
      } else {
         return null;
      }
   }

   protected MediaType getContentType(ClientHttpResponse response) {
      MediaType contentType = response.getHeaders().getContentType();
      if (contentType == null) {
         if (this.logger.isTraceEnabled()) {
            this.logger.trace("No content-type, using 'application/octet-stream'");
         }

         contentType = MediaType.APPLICATION_OCTET_STREAM;
      }

      return contentType;
   }

   private static byte[] getResponseBody(ClientHttpResponse response) {
      try {
         return FileCopyUtils.copyToByteArray(response.getBody());
      } catch (IOException var2) {
         return new byte[0];
      }
   }
}
