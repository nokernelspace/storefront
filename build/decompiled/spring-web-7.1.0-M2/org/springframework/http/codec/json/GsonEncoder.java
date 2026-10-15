package org.springframework.http.codec.json;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractEncoder;
import org.springframework.core.codec.EncodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageEncoder;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.Assert;
import org.springframework.util.FastByteArrayOutputStream;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class GsonEncoder extends AbstractEncoder<Object> implements HttpMessageEncoder<Object> {
   private static final byte[] NEWLINE_SEPARATOR = new byte[]{10};
   private static final byte[] EMPTY_BYTES = new byte[0];
   private static final MimeType[] DEFAULT_JSON_MIME_TYPES = new MimeType[]{
      MediaType.APPLICATION_JSON, new MediaType("application", "*+json"), MediaType.APPLICATION_NDJSON, MediaType.APPLICATION_JSONL
   };
   private final Gson gson;
   private final List<MediaType> streamingMediaTypes = new ArrayList<>(1);

   public GsonEncoder() {
      this(new Gson(), DEFAULT_JSON_MIME_TYPES);
      this.setStreamingMediaTypes(List.of(MediaType.APPLICATION_NDJSON, MediaType.APPLICATION_JSONL));
   }

   public GsonEncoder(Gson gson, MimeType... mimeTypes) {
      super(mimeTypes);
      Assert.notNull(gson, "A Gson instance is required");
      this.gson = gson;
   }

   public void setStreamingMediaTypes(List<MediaType> mediaTypes) {
      this.streamingMediaTypes.clear();
      this.streamingMediaTypes.addAll(mediaTypes);
   }

   @Override
   public List<MediaType> getStreamingMediaTypes() {
      return Collections.unmodifiableList(this.streamingMediaTypes);
   }

   public boolean canEncode(ResolvableType elementType, @Nullable MimeType mimeType) {
      if (!super.canEncode(elementType, mimeType)) {
         return false;
      }

      Class<?> elementClass = elementType.toClass();
      return !String.class.isAssignableFrom(elementClass) && !ServerSentEvent.class.isAssignableFrom(elementClass);
   }

   public Flux<DataBuffer> encode(
      Publisher<?> inputStream, DataBufferFactory bufferFactory, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints
   ) {
      boolean isStreaming = this.isStreamingMediaType(mimeType);
      if (isStreaming) {
         return Flux.from(inputStream).map(message -> this.encodeValue(message, bufferFactory, EMPTY_BYTES, NEWLINE_SEPARATOR));
      }

      GsonEncoder.JsonArrayJoinHelper helper = new GsonEncoder.JsonArrayJoinHelper();
      return Flux.from(inputStream)
         .map(value -> {
            byte[] prefix = helper.getPrefix();
            byte[] delimiter = helper.getDelimiter();
            DataBuffer dataBuffer = this.encodeValue(value, bufferFactory, delimiter, EMPTY_BYTES);
            return prefix.length > 0 ? bufferFactory.join(List.of(bufferFactory.wrap(prefix), dataBuffer)) : dataBuffer;
         })
         .switchIfEmpty(Mono.fromCallable(() -> bufferFactory.wrap(helper.getPrefix())))
         .concatWith(Mono.fromCallable(() -> bufferFactory.wrap(helper.getSuffix())));
   }

   public DataBuffer encodeValue(
      Object value, DataBufferFactory bufferFactory, ResolvableType valueType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints
   ) {
      return this.encodeValue(value, bufferFactory, EMPTY_BYTES, EMPTY_BYTES);
   }

   private DataBuffer encodeValue(Object value, DataBufferFactory bufferFactory, byte[] prefix, byte[] suffix) {
      try {
         FastByteArrayOutputStream bos = new FastByteArrayOutputStream();
         OutputStreamWriter writer = new OutputStreamWriter(bos, StandardCharsets.UTF_8);
         bos.write(prefix);
         this.gson.toJson(value, writer);
         writer.flush();
         bos.write(suffix);
         byte[] bytes = bos.toByteArrayUnsafe();
         return bufferFactory.wrap(bytes);
      } catch (IOException ex) {
         throw new EncodingException("JSON encoding error: " + ex.getMessage(), ex);
      }
   }

   protected boolean isStreamingMediaType(@Nullable MimeType mimeType) {
      for (MediaType streamingMediaType : this.streamingMediaTypes) {
         if (streamingMediaType.isCompatibleWith(mimeType)) {
            return true;
         }
      }

      return false;
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
         return GsonEncoder.EMPTY_BYTES;
      }

      public byte[] getPrefix() {
         return this.firstItemEmitted ? GsonEncoder.EMPTY_BYTES : OPEN_BRACKET;
      }

      public byte[] getSuffix() {
         return CLOSE_BRACKET;
      }
   }
}
