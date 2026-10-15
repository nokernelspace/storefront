package org.springframework.http.codec.json;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import kotlinx.serialization.json.Json;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.KotlinSerializationStringEncoder;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class KotlinSerializationJsonEncoder extends KotlinSerializationStringEncoder<Json> {
   private static final MimeType[] DEFAULT_JSON_MIME_TYPES = new MimeType[]{
      MediaType.APPLICATION_JSON, new MediaType("application", "*+json"), MediaType.APPLICATION_NDJSON, MediaType.APPLICATION_JSONL
   };

   public KotlinSerializationJsonEncoder() {
      this(Json.Default);
   }

   public KotlinSerializationJsonEncoder(Predicate<ResolvableType> typePredicate) {
      this(Json.Default, typePredicate);
   }

   public KotlinSerializationJsonEncoder(Json json) {
      super(json, DEFAULT_JSON_MIME_TYPES);
      this.setStreamingMediaTypes(List.of(MediaType.APPLICATION_NDJSON, MediaType.APPLICATION_JSONL));
   }

   public KotlinSerializationJsonEncoder(Json json, Predicate<ResolvableType> typePredicate) {
      super(json, typePredicate, DEFAULT_JSON_MIME_TYPES);
      this.setStreamingMediaTypes(List.of(MediaType.APPLICATION_NDJSON, MediaType.APPLICATION_JSONL));
   }

   @Override
   public Flux<DataBuffer> encodeNonStream(
      Publisher<?> inputStream, DataBufferFactory bufferFactory, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints
   ) {
      KotlinSerializationJsonEncoder.JsonArrayJoinHelper helper = new KotlinSerializationJsonEncoder.JsonArrayJoinHelper();
      return Flux.from(inputStream)
         .map(value -> this.encodeStreamingValue(value, bufferFactory, elementType, mimeType, hints, helper.getPrefix(), EMPTY_BYTES))
         .switchIfEmpty(Mono.fromCallable(() -> bufferFactory.wrap(helper.getPrefix())))
         .concatWith(Mono.fromCallable(() -> bufferFactory.wrap(helper.getSuffix())));
   }

   private static class JsonArrayJoinHelper {
      private static final byte[] COMMA_SEPARATOR = new byte[]{44};
      private static final byte[] OPEN_BRACKET = new byte[]{91};
      private static final byte[] CLOSE_BRACKET = new byte[]{93};
      private boolean firstItemEmitted;

      public byte[] getPrefix() {
         byte[] prefix = this.firstItemEmitted ? COMMA_SEPARATOR : OPEN_BRACKET;
         this.firstItemEmitted = true;
         return prefix;
      }

      public byte[] getSuffix() {
         return CLOSE_BRACKET;
      }
   }
}
