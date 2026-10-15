package org.springframework.http.converter;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.StreamingHttpOutputMessage;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

public class FormHttpMessageConverter implements SmartHttpMessageConverter<Object> {
   public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
   private Charset charset = DEFAULT_CHARSET;

   @Override
   public List<MediaType> getSupportedMediaTypes() {
      return List.of(MediaType.APPLICATION_FORM_URLENCODED);
   }

   public void setCharset(@Nullable Charset charset) {
      if (charset != this.charset) {
         this.charset = charset != null ? charset : DEFAULT_CHARSET;
      }
   }

   @Override
   public boolean canRead(ResolvableType type, @Nullable MediaType mediaType) {
      return this.canConvert(type, mediaType);
   }

   @Override
   public boolean canWrite(ResolvableType targetType, Class<?> valueClass, @Nullable MediaType mediaType) {
      return this.canConvert(targetType, mediaType);
   }

   private boolean canConvert(ResolvableType targetType, @Nullable MediaType mediaType) {
      if (!Map.class.isAssignableFrom(targetType.toClass())) {
         return false;
      }

      for (MediaType supportedMediaType : this.getSupportedMediaTypes()) {
         if (supportedMediaType.includes(mediaType)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public Object read(ResolvableType type, HttpInputMessage inputMessage, @Nullable Map<String, Object> hints) throws IOException, HttpMessageNotReadableException {
      MediaType contentType = inputMessage.getHeaders().getContentType();
      Charset charset = contentType != null && contentType.getCharset() != null ? contentType.getCharset() : this.charset;
      String body = StreamUtils.copyToString(inputMessage.getBody(), charset);
      String[] pairs = StringUtils.tokenizeToStringArray(body, "&");
      MultiValueMap<String, String> result = new LinkedMultiValueMap(pairs.length);

      try {
         for (String pair : pairs) {
            int idx = pair.indexOf(61);
            if (idx == -1) {
               result.add(URLDecoder.decode(pair, charset), null);
            } else {
               String name = URLDecoder.decode(pair.substring(0, idx), charset);
               String value = URLDecoder.decode(pair.substring(idx + 1), charset);
               result.add(name, value);
            }
         }
      } catch (IllegalArgumentException ex) {
         throw new HttpMessageNotReadableException("Could not decode HTTP form payload", ex, inputMessage);
      }

      return !MultiValueMap.class.isAssignableFrom(type.toClass()) ? result.asSingleValueMap() : result;
   }

   @Override
   public void write(Object data, ResolvableType type, @Nullable MediaType contentType, HttpOutputMessage outputMessage, @Nullable Map<String, Object> hints) throws IOException, HttpMessageNotWritableException {
      Assert.isInstanceOf(Map.class, data, "data must be of type Map or MultiValueMap");
      contentType = this.getFormContentType(contentType);
      outputMessage.getHeaders().setContentType(contentType);
      Charset charset = contentType.getCharset() != null ? contentType.getCharset() : this.charset;
      String serializedForm = "";
      if (data instanceof MultiValueMap<?, ?> formData) {
         serializedForm = this.serializeForm((MultiValueMap<String, String>)formData, charset);
      } else if (data instanceof Map<?, ?> formData) {
         serializedForm = this.serializeForm((Map<String, String>)formData, charset);
      }

      byte[] bytes = serializedForm.getBytes(charset);
      outputMessage.getHeaders().setContentLength(bytes.length);
      if (outputMessage instanceof StreamingHttpOutputMessage streamingOutputMessage) {
         streamingOutputMessage.setBody(bytes);
      } else {
         StreamUtils.copy(bytes, outputMessage.getBody());
      }
   }

   protected MediaType getFormContentType(@Nullable MediaType contentType) {
      if (contentType == null) {
         return MediaType.APPLICATION_FORM_URLENCODED;
      } else {
         return contentType.getCharset() == null && this.charset != DEFAULT_CHARSET ? new MediaType(contentType, this.charset) : contentType;
      }
   }

   protected String serializeForm(Map<String, String> formData, Charset charset) {
      StringBuilder builder = new StringBuilder();
      formData.forEach((name, value) -> {
         if (name == null) {
            Assert.isTrue(ObjectUtils.isEmpty(value), () -> "Null name in form data: " + formData);
         } else {
            this.serializeValue(builder, name, value, charset);
         }
      });
      return builder.toString();
   }

   protected String serializeForm(MultiValueMap<String, String> formData, Charset charset) {
      StringBuilder builder = new StringBuilder();
      formData.forEach((name, values) -> {
         if (name == null) {
            Assert.isTrue(CollectionUtils.isEmpty(values), () -> "Null name in form data: " + formData);
         } else {
            values.forEach(value -> this.serializeValue(builder, name, value, charset));
         }
      });
      return builder.toString();
   }

   private void serializeValue(StringBuilder builder, String name, String value, Charset charset) {
      if (builder.length() != 0) {
         builder.append('&');
      }

      builder.append(URLEncoder.encode(name, charset));
      if (value != null) {
         builder.append('=');
         builder.append(URLEncoder.encode(value, charset));
      }
   }
}
