package org.springframework.http.codec;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.CodecException;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.StringDecoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpInputMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ServerSentEventHttpMessageReader implements HttpMessageReader<Object> {
   private static final ResolvableType STRING_TYPE = ResolvableType.forClass(String.class);
   private final @Nullable Decoder<?> decoder;
   private final StringDecoder lineDecoder = StringDecoder.textPlainOnly();

   public ServerSentEventHttpMessageReader() {
      this(null);
   }

   public ServerSentEventHttpMessageReader(@Nullable Decoder<?> decoder) {
      this.decoder = decoder;
   }

   public @Nullable Decoder<?> getDecoder() {
      return this.decoder;
   }

   public void setMaxInMemorySize(int byteCount) {
      this.lineDecoder.setMaxInMemorySize(byteCount);
   }

   public int getMaxInMemorySize() {
      return this.lineDecoder.getMaxInMemorySize();
   }

   @Override
   public List<MediaType> getReadableMediaTypes() {
      return Collections.singletonList(MediaType.TEXT_EVENT_STREAM);
   }

   @Override
   public boolean canRead(ResolvableType elementType, @Nullable MediaType mediaType) {
      return MediaType.TEXT_EVENT_STREAM.includes(mediaType) || this.isServerSentEvent(elementType);
   }

   private boolean isServerSentEvent(ResolvableType elementType) {
      return ServerSentEvent.class.isAssignableFrom(elementType.toClass());
   }

   @Override
   public Flux<Object> read(ResolvableType elementType, ReactiveHttpInputMessage message, Map<String, Object> hints) {
      ServerSentEventHttpMessageReader.LimitTracker limitTracker = new ServerSentEventHttpMessageReader.LimitTracker();
      boolean shouldWrap = this.isServerSentEvent(elementType);
      ResolvableType valueType = shouldWrap ? elementType.getGeneric(new int[0]) : elementType;
      return this.lineDecoder
         .decode(message.getBody(), STRING_TYPE, null, hints)
         .doOnNext(limitTracker::afterLineParsed)
         .bufferUntil(String::isEmpty)
         .concatMap(lines -> {
            Object event = this.buildEvent(lines, valueType, shouldWrap, hints);
            return event != null ? Mono.just(event) : Mono.empty();
         });
   }

   private @Nullable Object buildEvent(List<String> lines, ResolvableType valueType, boolean shouldWrap, Map<String, Object> hints) {
      ServerSentEvent.Builder<Object> sseBuilder = shouldWrap ? ServerSentEvent.builder() : null;
      StringBuilder data = null;
      StringBuilder comment = null;

      for (String line : lines) {
         if (line.startsWith("data:")) {
            data = data != null ? data : new StringBuilder();
            int length = line.length();
            if (length > 5) {
               int index = line.charAt(5) != ' ' ? 5 : 6;
               if (length > index) {
                  data.append(line, index, line.length());
               }
            }

            data.append('\n');
         } else if (shouldWrap) {
            if (line.startsWith("id:")) {
               sseBuilder.id(line.substring(3).trim());
            } else if (line.startsWith("event:")) {
               sseBuilder.event(line.substring(6).trim());
            } else if (line.startsWith("retry:")) {
               Long retry = parseRetry(line.substring(6).trim());
               if (retry != null) {
                  sseBuilder.retry(Duration.ofMillis(retry));
               }
            } else if (line.startsWith(":")) {
               comment = comment != null ? comment : new StringBuilder();
               comment.append(line.substring(1).trim()).append('\n');
            }
         }
      }

      Object decodedData = data != null ? this.decodeData(data, valueType, hints) : null;
      if (shouldWrap) {
         if (comment != null) {
            sseBuilder.comment(comment.substring(0, comment.length() - 1));
         }

         if (decodedData != null) {
            sseBuilder.data(decodedData);
         }

         return sseBuilder.build();
      } else {
         return decodedData;
      }
   }

   private static @Nullable Long parseRetry(String value) {
      if (value.isEmpty()) {
         return null;
      }

      for (int i = 0; i < value.length(); i++) {
         char ch = value.charAt(i);
         if (ch < '0' || ch > '9') {
            return null;
         }
      }

      try {
         return Long.parseLong(value);
      } catch (NumberFormatException ex) {
         return null;
      }
   }

   private @Nullable Object decodeData(StringBuilder data, ResolvableType dataType, Map<String, Object> hints) {
      if (String.class == dataType.resolve()) {
         return data.substring(0, data.length() - 1);
      }

      if (this.decoder == null) {
         throw new CodecException("No SSE decoder configured and the data is not String.");
      }

      byte[] bytes = data.toString().getBytes(StandardCharsets.UTF_8);
      DataBuffer buffer = DefaultDataBufferFactory.sharedInstance.wrap(bytes);
      return this.decoder.decode(buffer, dataType, MediaType.TEXT_EVENT_STREAM, hints);
   }

   @Override
   public Mono<Object> readMono(ResolvableType elementType, ReactiveHttpInputMessage message, Map<String, Object> hints) {
      if (elementType.resolve() == String.class) {
         Flux<DataBuffer> body = message.getBody();
         return this.lineDecoder.decodeToMono(body, elementType, null, null).cast(Object.class);
      } else {
         return Mono.error(new UnsupportedOperationException("ServerSentEventHttpMessageReader only supports reading stream of events as a Flux"));
      }
   }

   private class LimitTracker {
      private int accumulated = 0;

      public void afterLineParsed(String line) {
         if (ServerSentEventHttpMessageReader.this.getMaxInMemorySize() >= 0) {
            if (line.isEmpty()) {
               this.accumulated = 0;
            }

            if (line.length() > Integer.MAX_VALUE - this.accumulated) {
               this.raiseLimitException();
            } else {
               this.accumulated = this.accumulated + line.length();
               if (this.accumulated > ServerSentEventHttpMessageReader.this.getMaxInMemorySize()) {
                  this.raiseLimitException();
               }
            }
         }
      }

      private void raiseLimitException() {
         throw new DataBufferLimitException("Exceeded limit on max bytes to buffer : " + ServerSentEventHttpMessageReader.this.getMaxInMemorySize());
      }
   }
}
