package org.springframework.http.codec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import kotlinx.serialization.KSerializer;
import kotlinx.serialization.StringFormat;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.CharSequenceEncoder;
import org.springframework.core.codec.Encoder;
import org.springframework.core.codec.EncodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class KotlinSerializationStringEncoder<T extends StringFormat> extends KotlinSerializationSupport<T> implements Encoder<Object> {
   private static final byte[] NEWLINE_SEPARATOR = new byte[]{10};
   protected static final byte[] EMPTY_BYTES = new byte[0];
   private final CharSequenceEncoder charSequenceEncoder = CharSequenceEncoder.allMimeTypes();
   private final Set<MimeType> streamingMediaTypes = new HashSet<>();

   protected KotlinSerializationStringEncoder(T format, MimeType... supportedMimeTypes) {
      super(format, supportedMimeTypes);
   }

   protected KotlinSerializationStringEncoder(T format, Predicate<ResolvableType> typePredicate, MimeType... supportedMimeTypes) {
      super(format, typePredicate, supportedMimeTypes);
   }

   public void setStreamingMediaTypes(Collection<MediaType> streamingMediaTypes) {
      this.streamingMediaTypes.clear();
      this.streamingMediaTypes.addAll(streamingMediaTypes);
   }

   public boolean canEncode(ResolvableType elementType, @Nullable MimeType mimeType) {
      return this.canSerialize(elementType, mimeType) && !String.class.isAssignableFrom(elementType.toClass());
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
      if (inputStream instanceof Mono<?> mono) {
         return mono.map(value -> this.encodeValue(value, bufferFactory, elementType, mimeType, hints)).flux();
      } else {
         return mimeType != null && this.streamingMediaTypes.contains(mimeType)
            ? Flux.from(inputStream)
               .map(value -> this.encodeStreamingValue(value, bufferFactory, elementType, mimeType, hints, EMPTY_BYTES, NEWLINE_SEPARATOR))
            : this.encodeNonStream(inputStream, bufferFactory, elementType, mimeType, hints);
      }
   }

   protected DataBuffer encodeStreamingValue(
      Object value,
      DataBufferFactory bufferFactory,
      ResolvableType valueType,
      @Nullable MimeType mimeType,
      @Nullable Map<String, Object> hints,
      byte[] prefix,
      byte[] suffix
   ) {
      List<DataBuffer> buffers = new ArrayList<>(3);
      if (prefix.length > 0) {
         buffers.add(bufferFactory.allocateBuffer(prefix.length).write(prefix));
      }

      buffers.add(this.encodeValue(value, bufferFactory, valueType, mimeType, hints));
      if (suffix.length > 0) {
         buffers.add(bufferFactory.allocateBuffer(suffix.length).write(suffix));
      }

      return bufferFactory.join(buffers);
   }

   protected Flux<DataBuffer> encodeNonStream(
      Publisher<?> inputStream, DataBufferFactory bufferFactory, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints
   ) {
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

      String string = this.format().encodeToString(serializer, value);
      return this.charSequenceEncoder.encodeValue(string, bufferFactory, valueType, mimeType, null);
   }
}
