package org.springframework.http.codec;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Hints;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

public class FormHttpMessageWriter extends LoggingCodecSupport implements HttpMessageWriter<MultiValueMap<String, String>> {
   public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
   private static final List<MediaType> MEDIA_TYPES = Collections.singletonList(MediaType.APPLICATION_FORM_URLENCODED);
   private static final ResolvableType MULTIVALUE_TYPE = ResolvableType.forClassWithGenerics(MultiValueMap.class, new Class[]{String.class, String.class});
   private Charset defaultCharset = DEFAULT_CHARSET;

   public void setDefaultCharset(Charset charset) {
      Assert.notNull(charset, "Charset must not be null");
      this.defaultCharset = charset;
   }

   public Charset getDefaultCharset() {
      return this.defaultCharset;
   }

   @Override
   public List<MediaType> getWritableMediaTypes() {
      return MEDIA_TYPES;
   }

   @Override
   public boolean canWrite(ResolvableType elementType, @Nullable MediaType mediaType) {
      if (!MultiValueMap.class.isAssignableFrom(elementType.toClass())) {
         return false;
      } else if (MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(mediaType)) {
         return true;
      } else {
         return mediaType == null ? MULTIVALUE_TYPE.isAssignableFrom(elementType) : false;
      }
   }

   @Override
   public Mono<Void> write(
      Publisher<? extends MultiValueMap<String, String>> inputStream,
      ResolvableType elementType,
      @Nullable MediaType mediaType,
      ReactiveHttpOutputMessage message,
      Map<String, Object> hints
   ) {
      mediaType = this.getMediaType(mediaType);
      message.getHeaders().setContentType(mediaType);
      Charset charset = mediaType.getCharset() != null ? mediaType.getCharset() : this.getDefaultCharset();
      return Mono.from(inputStream).flatMap(form -> {
         this.logFormData(form, hints);
         String value = this.serializeForm(form, charset);
         ByteBuffer byteBuffer = charset.encode(value);
         DataBuffer buffer = message.bufferFactory().wrap(byteBuffer);
         message.getHeaders().setContentLength(byteBuffer.remaining());
         return message.writeWith(Mono.just(buffer));
      });
   }

   protected MediaType getMediaType(@Nullable MediaType mediaType) {
      if (mediaType == null) {
         return MediaType.APPLICATION_FORM_URLENCODED;
      } else {
         return mediaType.getCharset() == null && this.defaultCharset != DEFAULT_CHARSET ? new MediaType(mediaType, this.defaultCharset) : mediaType;
      }
   }

   private void logFormData(MultiValueMap<String, String> form, Map<String, Object> hints) {
      LogFormatUtils.traceDebug(
         this.logger,
         traceOn -> Hints.getLogPrefix(hints)
            + "Writing "
            + (this.isEnableLoggingRequestDetails() ? LogFormatUtils.formatValue(form, !traceOn) : "form fields " + form.keySet() + " (content masked)")
      );
   }

   protected String serializeForm(MultiValueMap<String, String> formData, Charset charset) {
      StringBuilder builder = new StringBuilder();
      formData.forEach((name, values) -> values.forEach(value -> {
         if (builder.length() != 0) {
            builder.append('&');
         }

         builder.append(URLEncoder.encode(name, charset));
         if (value != null) {
            builder.append('=');
            builder.append(URLEncoder.encode(value, charset));
         }
      }));
      return builder.toString();
   }
}
