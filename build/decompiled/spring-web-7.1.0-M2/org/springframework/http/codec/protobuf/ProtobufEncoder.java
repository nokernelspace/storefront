package org.springframework.http.codec.protobuf;

import com.google.protobuf.Message;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageEncoder;
import org.springframework.util.FastByteArrayOutputStream;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ProtobufEncoder extends ProtobufCodecSupport implements HttpMessageEncoder<Message> {
   private List<MediaType> mediaTypes = iniMediaTypes(Arrays.asList(MIME_TYPES));

   @Override
   protected void setMimeTypes(List<MimeType> mimeTypes) {
      super.setMimeTypes(mimeTypes);
      this.mediaTypes = iniMediaTypes(mimeTypes);
   }

   private static List<MediaType> iniMediaTypes(List<MimeType> mimeTypes) {
      return mimeTypes.stream()
         .map(mimeType -> new MediaType(mimeType.getType(), mimeType.getSubtype(), Collections.singletonMap("delimited", "true")))
         .toList();
   }

   @Override
   public List<MediaType> getStreamingMediaTypes() {
      return this.mediaTypes;
   }

   public List<MimeType> getEncodableMimeTypes() {
      return this.getMimeTypes();
   }

   public boolean canEncode(ResolvableType elementType, @Nullable MimeType mimeType) {
      return Message.class.isAssignableFrom(elementType.toClass()) && this.supportsMimeType(mimeType);
   }

   public Flux<DataBuffer> encode(
      Publisher<? extends Message> inputStream,
      DataBufferFactory bufferFactory,
      ResolvableType elementType,
      @Nullable MimeType mimeType,
      @Nullable Map<String, Object> hints
   ) {
      return Flux.from(inputStream).map(message -> this.encodeValue(message, bufferFactory, !(inputStream instanceof Mono)));
   }

   public DataBuffer encodeValue(
      Message message, DataBufferFactory bufferFactory, ResolvableType valueType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints
   ) {
      return this.encodeValue(message, bufferFactory, false);
   }

   private DataBuffer encodeValue(Message message, DataBufferFactory bufferFactory, boolean delimited) {
      FastByteArrayOutputStream outputStream = new FastByteArrayOutputStream();

      try {
         this.writeMessage(message, delimited, outputStream);
         byte[] bytes = outputStream.toByteArrayUnsafe();
         return bufferFactory.wrap(bytes);
      } catch (IOException ex) {
         throw new IllegalStateException("Unexpected I/O error while writing to data buffer", ex);
      }
   }

   protected void writeMessage(Message message, boolean delimited, OutputStream outputStream) throws IOException {
      if (delimited) {
         message.writeDelimitedTo(outputStream);
      } else {
         message.writeTo(outputStream);
      }
   }
}
