package org.springframework.http.codec;

import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractEncoder;
import org.springframework.core.codec.Encoder;
import org.springframework.core.codec.Hints;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpLogging;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class EncoderHttpMessageWriter<T> implements HttpMessageWriter<T> {
   private static final Log logger = HttpLogging.forLogName(EncoderHttpMessageWriter.class);
   private final Encoder<T> encoder;
   private final List<MediaType> mediaTypes;
   private final @Nullable MediaType defaultMediaType;

   public EncoderHttpMessageWriter(Encoder<T> encoder) {
      Assert.notNull(encoder, "Encoder is required");
      initLogger(encoder);
      this.encoder = encoder;
      this.mediaTypes = MediaType.asMediaTypes(encoder.getEncodableMimeTypes());
      this.defaultMediaType = initDefaultMediaType(this.mediaTypes);
   }

   private static void initLogger(Encoder<?> encoder) {
      if (encoder instanceof AbstractEncoder<?> abstractEncoder && encoder.getClass().getName().startsWith("org.springframework.core.codec")) {
         Log logger = HttpLogging.forLog(abstractEncoder.getLogger());
         abstractEncoder.setLogger(logger);
      }
   }

   private static @Nullable MediaType initDefaultMediaType(List<MediaType> mediaTypes) {
      return mediaTypes.stream().filter(MimeType::isConcrete).findFirst().orElse(null);
   }

   public Encoder<T> getEncoder() {
      return this.encoder;
   }

   @Override
   public List<MediaType> getWritableMediaTypes() {
      return this.mediaTypes;
   }

   @Override
   public List<MediaType> getWritableMediaTypes(ResolvableType elementType) {
      return MediaType.asMediaTypes(this.getEncoder().getEncodableMimeTypes(elementType));
   }

   @Override
   public boolean canWrite(ResolvableType elementType, @Nullable MediaType mediaType) {
      return this.encoder.canEncode(elementType, mediaType);
   }

   @Override
   public Mono<Void> write(
      Publisher<? extends T> inputStream,
      ResolvableType elementType,
      @Nullable MediaType mediaType,
      ReactiveHttpOutputMessage message,
      Map<String, Object> hints
   ) {
      MediaType contentType = this.updateContentType(message, mediaType);
      Flux<DataBuffer> body = this.encoder.encode(inputStream, message.bufferFactory(), elementType, contentType, hints);
      if (inputStream instanceof Mono) {
         return body.singleOrEmpty().switchIfEmpty(Mono.defer(() -> {
            message.getHeaders().setContentType(null);
            message.getHeaders().setContentLength(0L);
            return message.setComplete().then(Mono.empty());
         })).flatMap(buffer -> {
            Hints.touchDataBuffer(buffer, hints, logger);
            message.getHeaders().setContentLength(buffer.readableByteCount());
            return message.writeWith(Mono.just(buffer).doOnDiscard(DataBuffer.class, DataBufferUtils::release));
         }).doOnDiscard(DataBuffer.class, DataBufferUtils::release);
      }

      if (this.isStreamingMediaType(contentType)) {
         return message.writeAndFlushWith(body.map(buffer -> {
            Hints.touchDataBuffer(buffer, hints, logger);
            return Mono.just(buffer).doOnDiscard(DataBuffer.class, DataBufferUtils::release);
         }));
      }

      if (logger.isDebugEnabled()) {
         body = body.doOnNext(buffer -> Hints.touchDataBuffer(buffer, hints, logger));
      }

      return message.writeWith(body);
   }

   private @Nullable MediaType updateContentType(ReactiveHttpOutputMessage message, @Nullable MediaType mediaType) {
      MediaType result = message.getHeaders().getContentType();
      if (result != null) {
         return result;
      }

      MediaType fallback = this.defaultMediaType;
      result = useFallback(mediaType, fallback) ? fallback : mediaType;
      if (result != null) {
         result = addDefaultCharset(result, fallback);
         message.getHeaders().setContentType(result);
      }

      return result;
   }

   private static boolean useFallback(@Nullable MediaType main, @Nullable MediaType fallback) {
      return main == null || !main.isConcrete() || main.equals(MediaType.APPLICATION_OCTET_STREAM) && fallback != null;
   }

   private static MediaType addDefaultCharset(MediaType main, @Nullable MediaType defaultType) {
      return main.getCharset() == null && defaultType != null && defaultType.getCharset() != null ? new MediaType(main, defaultType.getCharset()) : main;
   }

   private boolean isStreamingMediaType(@Nullable MediaType mediaType) {
      if (mediaType != null && this.encoder instanceof HttpMessageEncoder<?> httpMessageEncoder) {
         for (MediaType streamingMediaType : httpMessageEncoder.getStreamingMediaTypes()) {
            if (mediaType.isCompatibleWith(streamingMediaType) && this.matchParameters(mediaType, streamingMediaType)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean matchParameters(MediaType streamingMediaType, MediaType mediaType) {
      for (String name : streamingMediaType.getParameters().keySet()) {
         String s1 = streamingMediaType.getParameter(name);
         String s2 = mediaType.getParameter(name);
         if (StringUtils.hasText(s1) && StringUtils.hasText(s2) && !s1.equalsIgnoreCase(s2)) {
            return false;
         }
      }

      return true;
   }

   @Override
   public Mono<Void> write(
      Publisher<? extends T> inputStream,
      ResolvableType actualType,
      ResolvableType elementType,
      @Nullable MediaType mediaType,
      ServerHttpRequest request,
      ServerHttpResponse response,
      Map<String, Object> hints
   ) {
      Map<String, Object> allHints = Hints.merge(hints, this.getWriteHints(actualType, elementType, mediaType, request, response));
      return this.write(inputStream, elementType, mediaType, response, allHints);
   }

   protected Map<String, Object> getWriteHints(
      ResolvableType streamType, ResolvableType elementType, @Nullable MediaType mediaType, ServerHttpRequest request, ServerHttpResponse response
   ) {
      return this.encoder instanceof HttpMessageEncoder<?> httpMessageEncoder
         ? httpMessageEncoder.getEncodeHints(streamType, elementType, mediaType, request, response)
         : Hints.none();
   }
}
