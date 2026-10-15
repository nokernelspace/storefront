package org.springframework.http.codec;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import kotlinx.serialization.BinaryFormat;
import kotlinx.serialization.KSerializer;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.ByteArrayEncoder;
import org.springframework.core.codec.Encoder;
import org.springframework.core.codec.EncodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class KotlinSerializationBinaryEncoder<T extends BinaryFormat> extends KotlinSerializationSupport<T> implements Encoder<Object> {
   private final ByteArrayEncoder byteArrayEncoder = new ByteArrayEncoder();

   protected KotlinSerializationBinaryEncoder(T format, MimeType... supportedMimeTypes) {
      super(format, supportedMimeTypes);
   }

   protected KotlinSerializationBinaryEncoder(T format, Predicate<ResolvableType> typePredicate, MimeType... supportedMimeTypes) {
      super(format, typePredicate, supportedMimeTypes);
   }

   public boolean canEncode(ResolvableType elementType, @Nullable MimeType mimeType) {
      return this.canSerialize(elementType, mimeType);
   }

   public List<MimeType> getEncodableMimeTypes() {
      return this.supportedMimeTypes();
   }

   public List<MimeType> getEncodableMimeTypes(ResolvableType elementType) {
      return this.supportedMimeTypes();
   }

   public Flux<DataBuffer> encode(
      Publisher<?> inputStream, DataBufferFactory bufferFactory, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints
   ) {
      if (inputStream instanceof Mono) {
         return Mono.from(inputStream).map(value -> this.encodeValue(value, bufferFactory, elementType, mimeType, hints)).flux();
      }

      ResolvableType listType = ResolvableType.forClassWithGenerics(List.class, new ResolvableType[]{elementType});
      return Flux.from(inputStream).collectList().map(list -> this.encodeValue(list, bufferFactory, listType, mimeType, hints)).flux();
   }

   public DataBuffer encodeValue(
      Object value, DataBufferFactory bufferFactory, ResolvableType valueType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints
   ) {
      KSerializer<Object> serializer = this.serializer(valueType);
      if (serializer == null) {
         throw new EncodingException("Could not find KSerializer for " + valueType);
      }

      byte[] bytes = this.format().encodeToByteArray(serializer, value);
      return this.byteArrayEncoder.encodeValue(bytes, bufferFactory, valueType, mimeType, null);
   }
}
