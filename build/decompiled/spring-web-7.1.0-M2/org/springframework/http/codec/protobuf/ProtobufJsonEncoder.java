package org.springframework.http.codec.protobuf;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.Printer;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
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

public class ProtobufJsonEncoder implements HttpMessageEncoder<Message> {
   private static final byte[] NEWLINE_SEPARATOR = new byte[]{10};
   private static final byte[] EMPTY_BYTES = new byte[0];
   private static final ResolvableType MESSAGE_TYPE = ResolvableType.forClass(Message.class);
   private static final List<MimeType> defaultMimeTypes = List.of(
      MediaType.APPLICATION_JSON, new MediaType("application", "*+json"), MediaType.APPLICATION_NDJSON
   );
   private final Printer printer;

   public ProtobufJsonEncoder() {
      this(JsonFormat.printer());
   }

   public ProtobufJsonEncoder(Printer printer) {
      this.printer = printer;
   }

   @Override
   public List<MediaType> getStreamingMediaTypes() {
      return List.of(MediaType.APPLICATION_NDJSON, MediaType.APPLICATION_JSONL);
   }

   public List<MimeType> getEncodableMimeTypes() {
      return defaultMimeTypes;
   }

   public boolean canEncode(ResolvableType elementType, @Nullable MimeType mimeType) {
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

   public Flux<DataBuffer> encode(
      Publisher<? extends Message> inputStream,
      DataBufferFactory bufferFactory,
      ResolvableType elementType,
      @Nullable MimeType mimeType,
      @Nullable Map<String, Object> hints
   ) {
      if (inputStream instanceof Mono) {
         return Mono.from(inputStream).map(value -> this.encodeValue(value, bufferFactory, elementType, mimeType, hints)).flux();
      }

      byte[] separator = this.getStreamingMediaTypeSeparator(mimeType);
      if (separator != null) {
         return Flux.from(inputStream).map(value -> {
            DataBuffer dataBuffer = this.encodeValue(value, bufferFactory, MESSAGE_TYPE, mimeType, hints);
            return bufferFactory.join(List.of(dataBuffer, bufferFactory.wrap(separator)));
         });
      }

      ProtobufJsonEncoder.JsonArrayJoinHelper helper = new ProtobufJsonEncoder.JsonArrayJoinHelper();
      return Flux.from(inputStream)
         .map(
            value -> {
               byte[] prefix = helper.getPrefix();
               byte[] delimiter = helper.getDelimiter();
               DataBuffer delimiterBuffer = bufferFactory.wrap(delimiter);
               DataBuffer dataBuffer = this.encodeValue(value, bufferFactory, MESSAGE_TYPE, mimeType, hints);
               return prefix.length > 0
                  ? bufferFactory.join(List.of(bufferFactory.wrap(prefix), delimiterBuffer, dataBuffer))
                  : bufferFactory.join(List.of(delimiterBuffer, dataBuffer));
            }
         )
         .switchIfEmpty(Mono.fromCallable(() -> bufferFactory.wrap(helper.getPrefix())))
         .concatWith(Mono.fromCallable(() -> bufferFactory.wrap(helper.getSuffix())));
   }

   public DataBuffer encodeValue(
      Message message, DataBufferFactory bufferFactory, ResolvableType valueType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints
   ) {
      FastByteArrayOutputStream bos = new FastByteArrayOutputStream();
      OutputStreamWriter writer = new OutputStreamWriter(bos, StandardCharsets.UTF_8);

      try {
         this.printer.appendTo(message, writer);
         writer.flush();
         byte[] bytes = bos.toByteArrayUnsafe();
         return bufferFactory.wrap(bytes);
      } catch (IOException ex) {
         throw new IllegalStateException("Unexpected I/O error while writing to data buffer", ex);
      }
   }

   protected byte @Nullable [] getStreamingMediaTypeSeparator(@Nullable MimeType mimeType) {
      for (MediaType streamingMediaType : this.getStreamingMediaTypes()) {
         if (streamingMediaType.isCompatibleWith(mimeType)) {
            return NEWLINE_SEPARATOR;
         }
      }

      return null;
   }

   private static class JsonArrayJoinHelper {
      private static final byte[] COMMA_SEPARATOR = new byte[]{44};
      private static final byte[] OPEN_BRACKET = new byte[]{91};
      private static final byte[] CLOSE_BRACKET = new byte[]{93};
      private boolean firstItemEmitted;

      public byte[] getDelimiter() {
         if (this.firstItemEmitted) {
            return COMMA_SEPARATOR;
         }

         this.firstItemEmitted = true;
         return ProtobufJsonEncoder.EMPTY_BYTES;
      }

      public byte[] getPrefix() {
         return this.firstItemEmitted ? ProtobufJsonEncoder.EMPTY_BYTES : OPEN_BRACKET;
      }

      public byte[] getSuffix() {
         return CLOSE_BRACKET;
      }
   }
}
