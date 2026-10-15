package org.springframework.http.codec.json;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import kotlinx.serialization.KSerializer;
import kotlinx.serialization.builtins.BuiltinSerializersKt;
import kotlinx.serialization.json.Json;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.codec.KotlinSerializationStringDecoder;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class KotlinSerializationJsonDecoder extends KotlinSerializationStringDecoder<Json> {
   private static final MimeType[] DEFAULT_JSON_MIME_TYPES = new MimeType[]{
      MediaType.APPLICATION_JSON, new MediaType("application", "*+json"), MediaType.APPLICATION_NDJSON, MediaType.APPLICATION_JSONL
   };

   public KotlinSerializationJsonDecoder() {
      this(Json.Default);
   }

   public KotlinSerializationJsonDecoder(Predicate<ResolvableType> typePredicate) {
      this(Json.Default, typePredicate);
   }

   public KotlinSerializationJsonDecoder(Json json) {
      super(json, DEFAULT_JSON_MIME_TYPES);
   }

   public KotlinSerializationJsonDecoder(Json json, Predicate<ResolvableType> typePredicate) {
      super(json, typePredicate, DEFAULT_JSON_MIME_TYPES);
   }

   @Override
   public Flux<Object> decode(Publisher<DataBuffer> inputStream, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {
      return Flux.defer(
         () -> {
            KSerializer<Object> serializer = this.serializer(elementType);
            return (Publisher)(serializer == null
               ? Mono.error(new DecodingException("Could not find KSerializer for " + elementType))
               : this.stringDecoder.decode(inputStream, elementType, mimeType, hints).switchOnFirst((signal, flux) -> {
                  if (signal.hasValue()) {
                     String value = Objects.requireNonNull((String)signal.get());
                     if (value.stripLeading().startsWith("[") && !List.class.isAssignableFrom(elementType.toClass())) {
                        KSerializer<List<Object>> listSerializer = BuiltinSerializersKt.ListSerializer(serializer);
                        Flux var10000 = flux.flatMapIterable(string -> (Iterable)this.format().decodeFromString(listSerializer, string));
                        KotlinSerializationJsonDecoder rec$ = this;
                        return var10000.onErrorMap(IllegalArgumentException.class, x$0 -> rec$.processException(x$0));
                     } else {
                        return flux.handle((string, sink) -> {
                           try {
                              sink.next(this.format().decodeFromString(serializer, string));
                           } catch (IllegalArgumentException ex) {
                              sink.error(this.processException(ex));
                           }
                        });
                     }
                  } else {
                     return flux;
                  }
               }));
         }
      );
   }
}
