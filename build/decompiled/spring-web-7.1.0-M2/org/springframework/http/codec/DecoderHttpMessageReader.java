package org.springframework.http.codec;

import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractDecoder;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Hints;
import org.springframework.http.HttpLogging;
import org.springframework.http.HttpMessage;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpInputMessage;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class DecoderHttpMessageReader<T> implements HttpMessageReader<T> {
   private final Decoder<T> decoder;
   private final List<MediaType> mediaTypes;

   public DecoderHttpMessageReader(Decoder<T> decoder) {
      Assert.notNull(decoder, "Decoder is required");
      initLogger(decoder);
      this.decoder = decoder;
      this.mediaTypes = MediaType.asMediaTypes(decoder.getDecodableMimeTypes());
   }

   private static void initLogger(Decoder<?> decoder) {
      if (decoder instanceof AbstractDecoder<?> abstractDecoder && decoder.getClass().getName().startsWith("org.springframework.core.codec")) {
         Log logger = HttpLogging.forLog(abstractDecoder.getLogger());
         abstractDecoder.setLogger(logger);
      }
   }

   public Decoder<T> getDecoder() {
      return this.decoder;
   }

   @Override
   public List<MediaType> getReadableMediaTypes() {
      return this.mediaTypes;
   }

   @Override
   public List<MediaType> getReadableMediaTypes(ResolvableType elementType) {
      return MediaType.asMediaTypes(this.decoder.getDecodableMimeTypes(elementType));
   }

   @Override
   public boolean canRead(ResolvableType elementType, @Nullable MediaType mediaType) {
      return this.decoder.canDecode(elementType, mediaType);
   }

   @Override
   public Flux<T> read(ResolvableType elementType, ReactiveHttpInputMessage message, Map<String, Object> hints) {
      MediaType contentType = this.getContentType(message);
      Map<String, Object> allHints = Hints.merge(hints, this.getReadHints(elementType, message));
      return this.decoder.decode(message.getBody(), elementType, contentType, allHints);
   }

   @Override
   public Mono<T> readMono(ResolvableType elementType, ReactiveHttpInputMessage message, Map<String, Object> hints) {
      MediaType contentType = this.getContentType(message);
      Map<String, Object> allHints = Hints.merge(hints, this.getReadHints(elementType, message));
      return this.decoder.decodeToMono(message.getBody(), elementType, contentType, allHints);
   }

   protected @Nullable MediaType getContentType(HttpMessage inputMessage) {
      MediaType contentType = inputMessage.getHeaders().getContentType();
      return contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM;
   }

   protected Map<String, Object> getReadHints(ResolvableType elementType, ReactiveHttpInputMessage message) {
      return Hints.none();
   }

   @Override
   public Flux<T> read(ResolvableType actualType, ResolvableType elementType, ServerHttpRequest request, ServerHttpResponse response, Map<String, Object> hints) {
      Map<String, Object> allHints = Hints.merge(hints, this.getReadHints(actualType, elementType, request, response));
      return this.read(elementType, request, allHints);
   }

   @Override
   public Mono<T> readMono(
      ResolvableType actualType, ResolvableType elementType, ServerHttpRequest request, ServerHttpResponse response, Map<String, Object> hints
   ) {
      Map<String, Object> allHints = Hints.merge(hints, this.getReadHints(actualType, elementType, request, response));
      return this.readMono(elementType, request, allHints);
   }

   protected Map<String, Object> getReadHints(ResolvableType actualType, ResolvableType elementType, ServerHttpRequest request, ServerHttpResponse response) {
      return this.decoder instanceof HttpMessageDecoder<?> httpMessageDecoder
         ? httpMessageDecoder.getDecodeHints(actualType, elementType, request, response)
         : Hints.none();
   }
}
