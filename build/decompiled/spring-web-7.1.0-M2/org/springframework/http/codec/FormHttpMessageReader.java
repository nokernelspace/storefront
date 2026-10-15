package org.springframework.http.codec;

import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Hints;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpInputMessage;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class FormHttpMessageReader extends LoggingCodecSupport implements HttpMessageReader<MultiValueMap<String, String>> {
   public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
   private static final ResolvableType MULTIVALUE_STRINGS_TYPE = ResolvableType.forClassWithGenerics(
      MultiValueMap.class, new Class[]{String.class, String.class}
   );
   private Charset defaultCharset = DEFAULT_CHARSET;
   private int maxInMemorySize = 262144;

   public void setDefaultCharset(Charset charset) {
      Assert.notNull(charset, "Charset must not be null");
      this.defaultCharset = charset;
   }

   public Charset getDefaultCharset() {
      return this.defaultCharset;
   }

   public void setMaxInMemorySize(int byteCount) {
      this.maxInMemorySize = byteCount;
   }

   public int getMaxInMemorySize() {
      return this.maxInMemorySize;
   }

   @Override
   public boolean canRead(ResolvableType elementType, @Nullable MediaType mediaType) {
      if (!supportsMediaType(mediaType)) {
         return false;
      } else {
         return MultiValueMap.class.isAssignableFrom(elementType.toClass()) && elementType.hasUnresolvableGenerics()
            ? true
            : MULTIVALUE_STRINGS_TYPE.isAssignableFrom(elementType);
      }
   }

   private static boolean supportsMediaType(@Nullable MediaType mediaType) {
      return mediaType == null || MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(mediaType);
   }

   @Override
   public Flux<MultiValueMap<String, String>> read(ResolvableType elementType, ReactiveHttpInputMessage message, Map<String, Object> hints) {
      return Flux.from(this.readMono(elementType, message, hints));
   }

   @Override
   public Mono<MultiValueMap<String, String>> readMono(ResolvableType elementType, ReactiveHttpInputMessage message, Map<String, Object> hints) {
      MediaType contentType = message.getHeaders().getContentType();
      Charset charset = this.getMediaTypeCharset(contentType);
      return DataBufferUtils.join(message.getBody(), this.maxInMemorySize).map(buffer -> {
         String body = buffer.toString(charset);
         DataBufferUtils.release(buffer);
         MultiValueMap<String, String> formData = this.parseFormData(charset, body);
         this.logFormData(formData, hints);
         return formData;
      });
   }

   private void logFormData(MultiValueMap<String, String> formData, Map<String, Object> hints) {
      LogFormatUtils.traceDebug(
         this.logger,
         traceOn -> Hints.getLogPrefix(hints)
            + "Read "
            + (this.isEnableLoggingRequestDetails() ? LogFormatUtils.formatValue(formData, !traceOn) : "form fields " + formData.keySet() + " (content masked)")
      );
   }

   private Charset getMediaTypeCharset(@Nullable MediaType mediaType) {
      return mediaType != null && mediaType.getCharset() != null ? mediaType.getCharset() : this.getDefaultCharset();
   }

   private MultiValueMap<String, String> parseFormData(Charset charset, String body) {
      String[] pairs = StringUtils.tokenizeToStringArray(body, "&");
      MultiValueMap<String, String> result = new LinkedMultiValueMap(pairs.length);

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

      return result;
   }

   @Override
   public List<MediaType> getReadableMediaTypes() {
      return Collections.singletonList(MediaType.APPLICATION_FORM_URLENCODED);
   }
}
