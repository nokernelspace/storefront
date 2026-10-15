package org.springframework.http.codec.protobuf;

import com.google.protobuf.Message;
import com.google.protobuf.Message.Builder;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.Parser;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ProtobufJsonDecoder implements Decoder<Message> {
   protected static final int DEFAULT_MESSAGE_MAX_SIZE = 262144;
   private static final List<MimeType> defaultMimeTypes = List.of(MediaType.APPLICATION_JSON, new MediaType("application", "*+json"));
   private static final ConcurrentMap<Class<?>, Method> methodCache = new ConcurrentReferenceHashMap();
   private final Parser parser;
   private int maxMessageSize = 262144;

   public ProtobufJsonDecoder() {
      this(JsonFormat.parser());
   }

   public ProtobufJsonDecoder(Parser parser) {
      this.parser = parser;
   }

   public int getMaxMessageSize() {
      return this.maxMessageSize;
   }

   public void setMaxMessageSize(int maxMessageSize) {
      this.maxMessageSize = maxMessageSize;
   }

   public boolean canDecode(ResolvableType elementType, @Nullable MimeType mimeType) {
      return Message.class.isAssignableFrom(elementType.toClass()) && supportsMimeType(mimeType);
   }

   private static boolean supportsMimeType(@Nullable MimeType mimeType) {
      if (mimeType == null) {
         return false;
      }

      for (MimeType m : defaultMimeTypes) {
         if (m.isCompatibleWith(mimeType)) {
            return true;
         }
      }

      return false;
   }

   public List<MimeType> getDecodableMimeTypes() {
      return defaultMimeTypes;
   }

   public Flux<Message> decode(Publisher<DataBuffer> inputStream, ResolvableType targetType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {
      return Flux.error(new UnsupportedOperationException("Protobuf decoder does not support Flux, use Mono<List<...>> instead."));
   }

   public Message decode(DataBuffer dataBuffer, ResolvableType targetType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints) throws DecodingException {
      try {
         Builder builder = getMessageBuilder(targetType.toClass());
         this.parser.merge(new InputStreamReader(dataBuffer.asInputStream()), builder);
         return builder.build();
      } catch (Exception ex) {
         throw new DecodingException("Could not read Protobuf message: " + ex.getMessage(), ex);
      } finally {
         DataBufferUtils.release(dataBuffer);
      }
   }

   protected static Builder getMessageBuilder(Class<?> clazz) throws Exception {
      Method method = methodCache.get(clazz);
      if (method == null) {
         method = clazz.getMethod("newBuilder");
         methodCache.put(clazz, method);
      }

      return (Builder)method.invoke(clazz);
   }

   public Mono<Message> decodeToMono(
      Publisher<DataBuffer> inputStream, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints
   ) {
      return DataBufferUtils.join(inputStream, this.maxMessageSize)
         .map(dataBuffer -> this.decode(dataBuffer, elementType, mimeType, hints))
         .onErrorMap(DataBufferLimitException.class, exc -> new DecodingException("Could not decode JSON as Protobuf message", exc));
   }
}
