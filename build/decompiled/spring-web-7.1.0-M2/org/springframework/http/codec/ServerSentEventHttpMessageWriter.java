package org.springframework.http.codec;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.CodecException;
import org.springframework.core.codec.Encoder;
import org.springframework.core.codec.Hints;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpLogging;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.Assert;
import org.springframework.web.util.SseUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ServerSentEventHttpMessageWriter implements HttpMessageWriter<Object> {
   private static final MediaType DEFAULT_MEDIA_TYPE = new MediaType("text", "event-stream", StandardCharsets.UTF_8);
   private static final List<MediaType> WRITABLE_MEDIA_TYPES = Collections.singletonList(MediaType.TEXT_EVENT_STREAM);
   private static final Log logger = HttpLogging.forLogName(ServerSentEventHttpMessageWriter.class);
   private final @Nullable Encoder<?> encoder;

   public ServerSentEventHttpMessageWriter() {
      this(null);
   }

   public ServerSentEventHttpMessageWriter(@Nullable Encoder<?> encoder) {
      this.encoder = encoder;
   }

   public @Nullable Encoder<?> getEncoder() {
      return this.encoder;
   }

   @Override
   public List<MediaType> getWritableMediaTypes() {
      return WRITABLE_MEDIA_TYPES;
   }

   @Override
   public boolean canWrite(ResolvableType elementType, @Nullable MediaType mediaType) {
      return mediaType == null || MediaType.TEXT_EVENT_STREAM.includes(mediaType) || ServerSentEvent.class.isAssignableFrom(elementType.toClass());
   }

   @Override
   public Mono<Void> write(
      Publisher<?> input, ResolvableType elementType, @Nullable MediaType mediaType, ReactiveHttpOutputMessage message, Map<String, Object> hints
   ) {
      mediaType = mediaType != null && mediaType.getCharset() != null ? mediaType : DEFAULT_MEDIA_TYPE;
      DataBufferFactory bufferFactory = message.bufferFactory();
      message.getHeaders().setContentType(mediaType);
      return message.writeAndFlushWith(this.encode(input, elementType, mediaType, bufferFactory, hints));
   }

   private Flux<Publisher<DataBuffer>> encode(
      Publisher<?> input, ResolvableType elementType, MediaType mediaType, DataBufferFactory factory, Map<String, Object> hints
   ) {
      ResolvableType dataType = ServerSentEvent.class.isAssignableFrom(elementType.toClass()) ? elementType.getGeneric(new int[0]) : elementType;
      return Flux.from(input)
         .map(
            element -> {
               ServerSentEvent<?> sse = element instanceof ServerSentEvent<?> serverSentEvent
                  ? serverSentEvent
                  : ServerSentEvent.<Object>builder().data(element).build();
               String sseText = sse.format();
               Object data = sse.data();
               Flux<DataBuffer> result;
               if (data == null) {
                  result = Flux.just(this.encodeText(sseText + "\n", mediaType, factory));
               } else if (data instanceof String text) {
                  StringBuilder sb = new StringBuilder(sseText);
                  this.writeStringData(text, sb);
                  result = Flux.just(this.encodeText(sb.toString(), mediaType, factory));
               } else {
                  result = this.encodeEvent(sseText, data, dataType, mediaType, factory, hints);
               }

               return result.doOnDiscard(DataBuffer.class, DataBufferUtils::release);
            }
         );
   }

   private void writeStringData(String input, StringBuilder sb) {
      SseUtils.appendFieldValue("data", input, sb);
      sb.append("\n\n");
   }

   private <T> Flux<DataBuffer> encodeEvent(
      CharSequence sseText, T data, ResolvableType dataType, MediaType mediaType, DataBufferFactory factory, Map<String, Object> hints
   ) {
      if (this.encoder == null) {
         throw new CodecException("No SSE encoder configured and the data is not String.");
      } else {
         return Flux.defer(() -> {
            DataBuffer startBuffer = this.encodeText(sseText, mediaType, factory);
            DataBuffer endBuffer = this.encodeText("\n\n", mediaType, factory);
            DataBuffer dataBuffer = this.encoder.encodeValue(data, factory, dataType, mediaType, hints);
            Hints.touchDataBuffer(dataBuffer, hints, logger);
            return Flux.just(new DataBuffer[]{startBuffer, dataBuffer, endBuffer});
         });
      }
   }

   private DataBuffer encodeText(CharSequence text, MediaType mediaType, DataBufferFactory bufferFactory) {
      Assert.notNull(mediaType.getCharset(), "Expected MediaType with charset");
      byte[] bytes = text.toString().getBytes(mediaType.getCharset());
      return bufferFactory.wrap(bytes);
   }

   @Override
   public Mono<Void> write(
      Publisher<?> input,
      ResolvableType actualType,
      ResolvableType elementType,
      @Nullable MediaType mediaType,
      ServerHttpRequest request,
      ServerHttpResponse response,
      Map<String, Object> hints
   ) {
      Map<String, Object> allHints = Hints.merge(hints, this.getEncodeHints(actualType, elementType, mediaType, request, response));
      return this.write(input, elementType, mediaType, response, allHints);
   }

   private Map<String, Object> getEncodeHints(
      ResolvableType actualType, ResolvableType elementType, @Nullable MediaType mediaType, ServerHttpRequest request, ServerHttpResponse response
   ) {
      return this.encoder instanceof HttpMessageEncoder<?> httpMessageEncoder
         ? httpMessageEncoder.getEncodeHints(actualType, elementType, mediaType, request, response)
         : Hints.none();
   }
}
