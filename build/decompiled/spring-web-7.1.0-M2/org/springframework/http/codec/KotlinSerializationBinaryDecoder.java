package org.springframework.http.codec;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import kotlinx.serialization.BinaryFormat;
import kotlinx.serialization.KSerializer;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.ByteArrayDecoder;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class KotlinSerializationBinaryDecoder<T extends BinaryFormat> extends KotlinSerializationSupport<T> implements Decoder<Object> {
   private final ByteArrayDecoder byteArrayDecoder = new ByteArrayDecoder();

   public KotlinSerializationBinaryDecoder(T format, MimeType... supportedMimeTypes) {
      super(format, supportedMimeTypes);
   }

   public KotlinSerializationBinaryDecoder(T format, Predicate<ResolvableType> typePredicate, MimeType... supportedMimeTypes) {
      super(format, typePredicate, supportedMimeTypes);
   }

   public void setMaxInMemorySize(int byteCount) {
      this.byteArrayDecoder.setMaxInMemorySize(byteCount);
   }

   public int getMaxInMemorySize() {
      return this.byteArrayDecoder.getMaxInMemorySize();
   }

   public boolean canDecode(ResolvableType elementType, @Nullable MimeType mimeType) {
      return this.canSerialize(elementType, mimeType);
   }

   public List<MimeType> getDecodableMimeTypes() {
      return this.supportedMimeTypes();
   }

   public List<MimeType> getDecodableMimeTypes(ResolvableType targetType) {
      return this.supportedMimeTypes();
   }

   public Flux<Object> decode(Publisher<DataBuffer> inputStream, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {
      return Flux.error(new UnsupportedOperationException());
   }

   public Mono<Object> decodeToMono(
      Publisher<DataBuffer> inputStream, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints
   ) {
      return Mono.defer(
         () -> {
            KSerializer<Object> serializer = this.serializer(elementType);
            return serializer == null
               ? Mono.error(new DecodingException("Could not find KSerializer for " + elementType))
               : this.byteArrayDecoder.decodeToMono(inputStream, elementType, mimeType, hints).handle((byteArray, sink) -> {
                  try {
                     sink.next(this.format().decodeFromByteArray(serializer, byteArray));
                     sink.complete();
                  } catch (IllegalArgumentException ex) {
                     sink.error(new DecodingException("Decoding error: " + ex.getMessage(), ex));
                  }
               });
         }
      );
   }
}
