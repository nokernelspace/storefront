package org.springframework.http.codec.protobuf;

import com.google.protobuf.Message;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Message.Builder;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Encoder;
import org.springframework.core.codec.EncodingException;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.codec.EncoderHttpMessageWriter;
import org.springframework.http.codec.HttpMessageEncoder;
import org.springframework.util.ConcurrentReferenceHashMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ProtobufHttpMessageWriter extends EncoderHttpMessageWriter<Message> {
   private static final String X_PROTOBUF_SCHEMA_HEADER = "X-Protobuf-Schema";
   private static final String X_PROTOBUF_MESSAGE_HEADER = "X-Protobuf-Message";
   private static final ConcurrentMap<Class<?>, Method> methodCache = new ConcurrentReferenceHashMap();

   public ProtobufHttpMessageWriter() {
      super(new ProtobufEncoder());
   }

   public ProtobufHttpMessageWriter(Encoder<Message> encoder) {
      super(encoder);
   }

   @Override
   public Mono<Void> write(
      Publisher<? extends Message> inputStream,
      ResolvableType elementType,
      @Nullable MediaType mediaType,
      ReactiveHttpOutputMessage outputMessage,
      Map<String, Object> hints
   ) {
      try {
         Builder builder = getMessageBuilder(elementType.toClass());
         Descriptor descriptor = builder.getDescriptorForType();
         outputMessage.getHeaders().add("X-Protobuf-Schema", descriptor.getFile().getName());
         outputMessage.getHeaders().add("X-Protobuf-Message", descriptor.getFullName());
         if (inputStream instanceof Flux) {
            outputMessage.getHeaders().setContentType(this.getStreamingContentType(mediaType));
         }

         this.extendHeaders(outputMessage, hints);
         return super.write(inputStream, elementType, mediaType, outputMessage, hints);
      } catch (Exception ex) {
         return Mono.error(new EncodingException("Could not write Protobuf message: " + ex.getMessage(), ex));
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

   protected MediaType getStreamingContentType(@Nullable MediaType mediaType) {
      if (mediaType == null) {
         return ((HttpMessageEncoder)this.getEncoder()).getStreamingMediaTypes().get(0);
      }

      Map<String, String> params = new HashMap<>(mediaType.getParameters());
      if (!"true".equals(params.get("delimited"))) {
         params.put("delimited", "true");
         mediaType = new MediaType(mediaType, params);
      }

      return mediaType;
   }

   protected void extendHeaders(ReactiveHttpOutputMessage message, Map<String, Object> hints) {
   }
}
