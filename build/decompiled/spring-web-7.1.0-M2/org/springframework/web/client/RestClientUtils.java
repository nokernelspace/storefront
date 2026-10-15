package org.springframework.web.client;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMessage;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.SmartHttpMessageConverter;
import org.springframework.util.FileCopyUtils;

abstract class RestClientUtils {
   private static final Log logger = LogFactory.getLog(DefaultRestClient.class);

   public static byte[] getBody(HttpInputMessage message) {
      try {
         return FileCopyUtils.copyToByteArray(message.getBody());
      } catch (IOException var2) {
         return new byte[0];
      }
   }

   public static @Nullable Charset getCharset(HttpMessage response) {
      HttpHeaders headers = response.getHeaders();
      MediaType contentType = headers.getContentType();
      return contentType != null ? contentType.getCharset() : null;
   }

   static <T> @Nullable T readWithMessageConverters(
      ClientHttpResponse response, List<HttpMessageConverter<?>> messageConverters, Type bodyType, Class<T> bodyClass, @Nullable Map<String, Object> hints
   ) throws IOException {
      IntrospectingClientHttpResponse responseWrapper = new IntrospectingClientHttpResponse(response);
      if (responseWrapper.hasMessageBody() && !responseWrapper.hasEmptyMessageBody()) {
         MediaType contentType = getContentType(responseWrapper);

         try {
            for (HttpMessageConverter<?> messageConverter : messageConverters) {
               if (messageConverter instanceof GenericHttpMessageConverter genericMessageConverter) {
                  if (genericMessageConverter.canRead(bodyType, null, contentType)) {
                     if (logger.isDebugEnabled()) {
                        logger.debug("Reading to [" + ResolvableType.forType(bodyType) + "]");
                     }

                     return (T)genericMessageConverter.read(bodyType, null, responseWrapper);
                  }
               } else if (messageConverter instanceof SmartHttpMessageConverter smartMessageConverter) {
                  ResolvableType resolvableType = ResolvableType.forType(bodyType);
                  if (smartMessageConverter.canRead(resolvableType, contentType)) {
                     if (logger.isDebugEnabled()) {
                        logger.debug("Reading to [" + resolvableType + "]");
                     }

                     return (T)smartMessageConverter.read(resolvableType, responseWrapper, hints);
                  }
               } else if (messageConverter.canRead(bodyClass, contentType)) {
                  if (logger.isDebugEnabled()) {
                     logger.debug("Reading to [" + bodyClass.getName() + "] as \"" + contentType + "\"");
                  }

                  return (T)messageConverter.read(bodyClass, responseWrapper);
               }
            }
         } catch (IOException | HttpMessageNotReadableException ex) {
            throw new RestClientException(
               "Error while extracting response for type [" + ResolvableType.forType(bodyType) + "] and content type [" + contentType + "]", ex
            );
         }

         throw new UnknownContentTypeException(
            bodyType, contentType, responseWrapper.getStatusCode(), responseWrapper.getStatusText(), responseWrapper.getHeaders(), getBody(responseWrapper)
         );
      } else {
         return null;
      }
   }

   static MediaType getContentType(HttpMessage message) {
      MediaType contentType = message.getHeaders().getContentType();
      return contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM;
   }
}
