package org.springframework.http.codec;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import kotlinx.serialization.KSerializer;
import kotlinx.serialization.StringFormat;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.CodecException;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.codec.StringDecoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class KotlinSerializationStringDecoder<T extends StringFormat> extends KotlinSerializationSupport<T> implements Decoder<Object> {
   protected final StringDecoder stringDecoder = StringDecoder.allMimeTypes(StringDecoder.DEFAULT_DELIMITERS, false);

   public KotlinSerializationStringDecoder(T format, MimeType... supportedMimeTypes) {
      super(format, supportedMimeTypes);
   }

   public KotlinSerializationStringDecoder(T format, Predicate<ResolvableType> typePredicate, MimeType... supportedMimeTypes) {
      super(format, typePredicate, supportedMimeTypes);
   }

   public void setMaxInMemorySize(int byteCount) {
      this.stringDecoder.setMaxInMemorySize(byteCount);
   }

   public int getMaxInMemorySize() {
      return this.stringDecoder.getMaxInMemorySize();
   }

   public boolean canDecode(ResolvableType elementType, @Nullable MimeType mimeType) {
      return this.canSerialize(elementType, mimeType) && !CharSequence.class.isAssignableFrom(elementType.toClass());
   }

   public List<MimeType> getDecodableMimeTypes() {
      return this.supportedMimeTypes();
   }

   public List<MimeType> getDecodableMimeTypes(ResolvableType targetType) {
      return this.supportedMimeTypes();
   }

   public Flux<Object> decode(Publisher<DataBuffer> inputStream, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {
      return Flux.defer(
         () -> {
            KSerializer<Object> serializer = this.serializer(elementType);
            return (Publisher)(serializer == null
               ? Mono.error(new DecodingException("Could not find KSerializer for " + elementType))
               : this.stringDecoder.decode(inputStream, elementType, mimeType, hints).handle((string, sink) -> {
                  try {
                     sink.next(this.format().decodeFromString(serializer, string));
                  } catch (IllegalArgumentException ex) {
                     sink.error(this.processException(ex));
                  }
               }));
         }
      );
   }

   public Mono<Object> decodeToMono(
      Publisher<DataBuffer> inputStream, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints
   ) {
      return Mono.defer(
         () -> {
            KSerializer<Object> serializer = this.serializer(elementType);
            return serializer == null
               ? Mono.error(new DecodingException("Could not find KSerializer for " + elementType))
               : this.stringDecoder.decodeToMono(inputStream, elementType, mimeType, hints).handle((string, sink) -> {
                  try {
                     sink.next(this.format().decodeFromString(serializer, string));
                     sink.complete();
                  } catch (IllegalArgumentException ex) {
                     sink.error(this.processException(ex));
                  }
               });
         }
      );
   }

   protected CodecException processException(IllegalArgumentException ex) {
      return new DecodingException("Decoding error: " + ex.getMessage(), ex);
   }
}
