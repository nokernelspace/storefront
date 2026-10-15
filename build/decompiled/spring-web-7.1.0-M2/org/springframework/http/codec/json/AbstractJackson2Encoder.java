package org.springframework.http.codec.json;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.BufferRecycler;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.CodecException;
import org.springframework.core.codec.EncodingException;
import org.springframework.core.codec.Hints;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageEncoder;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

@Deprecated(since = "7.0", forRemoval = true)
public abstract class AbstractJackson2Encoder extends Jackson2CodecSupport implements HttpMessageEncoder<Object> {
   private static final byte[] NEWLINE_SEPARATOR = new byte[]{10};
   private static final byte[] EMPTY_BYTES = new byte[0];
   private static final Map<String, JsonEncoding> ENCODINGS = CollectionUtils.newHashMap(JsonEncoding.values().length);
   private final List<MediaType> streamingMediaTypes = new ArrayList<>(1);

   protected AbstractJackson2Encoder(ObjectMapper mapper, MimeType... mimeTypes) {
      super(mapper, mimeTypes);
   }

   public void setStreamingMediaTypes(List<MediaType> mediaTypes) {
      this.streamingMediaTypes.clear();
      this.streamingMediaTypes.addAll(mediaTypes);
   }

   public boolean canEncode(ResolvableType elementType, @Nullable MimeType mimeType) {
      if (!this.supportsMimeType(mimeType)) {
         return false;
      }

      if (mimeType != null && mimeType.getCharset() != null) {
         Charset charset = mimeType.getCharset();
         if (!ENCODINGS.containsKey(charset.name())) {
            return false;
         }
      }

      ObjectMapper mapper = this.selectObjectMapper(elementType, mimeType);
      if (mapper == null) {
         return false;
      }

      Class<?> clazz = elementType.toClass();
      if (String.class.isAssignableFrom(elementType.resolve(clazz))) {
         return false;
      }

      if (Object.class == clazz) {
         return true;
      }

      if (!this.logger.isDebugEnabled()) {
         return mapper.canSerialize(clazz);
      }

      AtomicReference<Throwable> causeRef = new AtomicReference<>();
      if (mapper.canSerialize(clazz, causeRef)) {
         return true;
      }

      this.logWarningIfNecessary(clazz, causeRef.get());
      return false;
   }

   public Flux<DataBuffer> encode(
      Publisher<?> inputStream, DataBufferFactory bufferFactory, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints
   ) {
      Assert.notNull(inputStream, "'inputStream' must not be null");
      Assert.notNull(bufferFactory, "'bufferFactory' must not be null");
      Assert.notNull(elementType, "'elementType' must not be null");
      return Flux.deferContextual(
         contextView -> {
            Map<String, Object> hintsToUse = contextView.isEmpty() ? hints : Hints.merge(hints, ContextView.class.getName(), contextView);
            if (inputStream instanceof Mono) {
               return Mono.from(inputStream).map(value -> this.encodeValue(value, bufferFactory, elementType, mimeType, hintsToUse)).flux();
            }

            try {
               ObjectMapper mapper = this.selectObjectMapper(elementType, mimeType);
               if (mapper == null) {
                  throw new IllegalStateException("No ObjectMapper for " + elementType);
               }

               ObjectWriter writer = this.createObjectWriter(mapper, elementType, mimeType, null, hintsToUse);
               BufferRecycler recycler = writer.getFactory()._getBufferRecycler();
               ByteArrayBuilder byteBuilder = new ByteArrayBuilder(recycler);
               JsonEncoding encoding = this.getJsonEncoding(mimeType);
               JsonGenerator generator = mapper.getFactory().createGenerator(byteBuilder, encoding);
               SequenceWriter sequenceWriter = writer.writeValues(generator);
               byte[] separator = this.getStreamingMediaTypeSeparator(mimeType);
               Flux<DataBuffer> dataBufferFlux;
               if (separator != null) {
                  dataBufferFlux = Flux.from(inputStream)
                     .map(value -> this.encodeStreamingValue(value, bufferFactory, hintsToUse, sequenceWriter, byteBuilder, EMPTY_BYTES, separator));
               } else {
                  AbstractJackson2Encoder.JsonArrayJoinHelper helper = new AbstractJackson2Encoder.JsonArrayJoinHelper();
                  dataBufferFlux = Flux.from(inputStream)
                     .map(value -> {
                        byte[] prefix = helper.getPrefix();
                        byte[] delimiter = helper.getDelimiter();
                        DataBuffer dataBuffer = this.encodeStreamingValue(value, bufferFactory, hintsToUse, sequenceWriter, byteBuilder, delimiter, EMPTY_BYTES);
                        return prefix.length > 0 ? bufferFactory.join(List.of(bufferFactory.wrap(prefix), dataBuffer)) : dataBuffer;
                     })
                     .switchIfEmpty(Mono.fromCallable(() -> bufferFactory.wrap(helper.getPrefix())))
                     .concatWith(Mono.fromCallable(() -> bufferFactory.wrap(helper.getSuffix())));
               }

               return dataBufferFlux.doOnNext(dataBuffer -> Hints.touchDataBuffer(dataBuffer, hintsToUse, this.logger)).doAfterTerminate(() -> {
                  try {
                     generator.close();
                     byteBuilder.release();
                  } catch (IOException ex) {
                     this.logger.error("Could not close Encoder resources", exx);
                  } finally {
                     recycler.releaseToPool();
                  }
               });
            } catch (IOException ex) {
               return Flux.error(ex);
            }
         }
      );
   }

   public DataBuffer encodeValue(
      Object value, DataBufferFactory bufferFactory, ResolvableType valueType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints
   ) {
      Class<?> jsonView = null;
      FilterProvider filters = null;
      if (value instanceof MappingJacksonValue mappingJacksonValue) {
         value = mappingJacksonValue.getValue();
         valueType = ResolvableType.forInstance(value);
         jsonView = mappingJacksonValue.getSerializationView();
         filters = mappingJacksonValue.getFilters();
      }

      ObjectMapper mapper = this.selectObjectMapper(valueType, mimeType);
      if (mapper == null) {
         throw new IllegalStateException("No ObjectMapper for " + valueType);
      }

      ObjectWriter writer = this.createObjectWriter(mapper, valueType, mimeType, jsonView, hints);
      if (filters != null) {
         writer = writer.with(filters);
      }

      BufferRecycler recycler = writer.getFactory()._getBufferRecycler();
      ByteArrayBuilder byteBuilder = new ByteArrayBuilder(recycler);

      try {
         JsonEncoding encoding = this.getJsonEncoding(mimeType);
         this.logValue(hints, value);

         try {
            JsonGenerator generator = mapper.getFactory().createGenerator(byteBuilder, encoding);

            try {
               writer.writeValue(generator, value);
               generator.flush();
            } catch (Throwable var24) {
               if (generator != null) {
                  try {
                     generator.close();
                  } catch (Throwable var23) {
                     var24.addSuppressed(var23);
                  }
               }

               throw var24;
            }

            if (generator != null) {
               generator.close();
            }
         } catch (InvalidDefinitionException ex) {
            throw new CodecException("Type definition error: " + ex.getType(), ex);
         } catch (JsonProcessingException ex) {
            throw new EncodingException("JSON encoding error: " + ex.getOriginalMessage(), ex);
         } catch (IOException ex) {
            throw new IllegalStateException("Unexpected I/O error while writing to byte array builder", ex);
         }

         byte[] bytes = byteBuilder.toByteArray();
         DataBuffer buffer = bufferFactory.allocateBuffer(bytes.length);
         buffer.write(bytes);
         Hints.touchDataBuffer(buffer, hints, this.logger);
         return buffer;
      } finally {
         byteBuilder.release();
         recycler.releaseToPool();
      }
   }

   private DataBuffer encodeStreamingValue(
      Object value,
      DataBufferFactory bufferFactory,
      @Nullable Map<String, Object> hints,
      SequenceWriter sequenceWriter,
      ByteArrayBuilder byteArrayBuilder,
      byte[] prefix,
      byte[] suffix
   ) {
      this.logValue(hints, value);

      try {
         sequenceWriter.write(value);
         sequenceWriter.flush();
      } catch (InvalidDefinitionException ex) {
         throw new CodecException("Type definition error: " + ex.getType(), ex);
      } catch (JsonProcessingException ex) {
         throw new EncodingException("JSON encoding error: " + ex.getOriginalMessage(), ex);
      } catch (IOException ex) {
         throw new IllegalStateException("Unexpected I/O error while writing to byte array builder", ex);
      }

      byte[] bytes = byteArrayBuilder.toByteArray();
      byteArrayBuilder.reset();
      int offset;
      int length;
      if (bytes.length > 0 && bytes[0] == 32) {
         offset = 1;
         length = bytes.length - 1;
      } else {
         offset = 0;
         length = bytes.length;
      }

      DataBuffer buffer = bufferFactory.allocateBuffer(length + prefix.length + suffix.length);
      if (prefix.length != 0) {
         buffer.write(prefix);
      }

      buffer.write(bytes, offset, length);
      if (suffix.length != 0) {
         buffer.write(suffix);
      }

      Hints.touchDataBuffer(buffer, hints, this.logger);
      return buffer;
   }

   private void logValue(@Nullable Map<String, Object> hints, Object value) {
      if (!Hints.isLoggingSuppressed(hints)) {
         LogFormatUtils.traceDebug(this.logger, traceOn -> {
            String formatted = LogFormatUtils.formatValue(value, !traceOn);
            return Hints.getLogPrefix(hints) + "Encoding [" + formatted + "]";
         });
      }
   }

   private ObjectWriter createObjectWriter(
      ObjectMapper mapper, ResolvableType valueType, @Nullable MimeType mimeType, @Nullable Class<?> jsonView, @Nullable Map<String, Object> hints
   ) {
      JavaType javaType = this.getJavaType(valueType.getType(), null);
      if (jsonView == null && hints != null) {
         jsonView = (Class<?>)hints.get(Jackson2CodecSupport.JSON_VIEW_HINT);
      }

      ObjectWriter writer = jsonView != null ? mapper.writerWithView(jsonView) : mapper.writer();
      if (javaType.isContainerType()) {
         writer = writer.forType(javaType);
      }

      return this.customizeWriter(writer, mimeType, valueType, hints);
   }

   protected ObjectWriter customizeWriter(ObjectWriter writer, @Nullable MimeType mimeType, ResolvableType elementType, @Nullable Map<String, Object> hints) {
      return writer;
   }

   protected byte @Nullable [] getStreamingMediaTypeSeparator(@Nullable MimeType mimeType) {
      for (MediaType streamingMediaType : this.streamingMediaTypes) {
         if (streamingMediaType.isCompatibleWith(mimeType)) {
            return NEWLINE_SEPARATOR;
         }
      }

      return null;
   }

   protected JsonEncoding getJsonEncoding(@Nullable MimeType mimeType) {
      if (mimeType != null && mimeType.getCharset() != null) {
         Charset charset = mimeType.getCharset();
         JsonEncoding result = ENCODINGS.get(charset.name());
         if (result != null) {
            return result;
         }
      }

      return JsonEncoding.UTF8;
   }

   public List<MimeType> getEncodableMimeTypes() {
      return this.getMimeTypes();
   }

   public List<MimeType> getEncodableMimeTypes(ResolvableType elementType) {
      return this.getMimeTypes(elementType);
   }

   @Override
   public List<MediaType> getStreamingMediaTypes() {
      return Collections.unmodifiableList(this.streamingMediaTypes);
   }

   @Override
   public Map<String, Object> getEncodeHints(
      @Nullable ResolvableType actualType, ResolvableType elementType, @Nullable MediaType mediaType, ServerHttpRequest request, ServerHttpResponse response
   ) {
      return actualType != null ? this.getHints(actualType) : Hints.none();
   }

   @Override
   protected <A extends Annotation> @Nullable A getAnnotation(MethodParameter parameter, Class<A> annotType) {
      return (A)parameter.getMethodAnnotation(annotType);
   }

   static {
      for (JsonEncoding encoding : JsonEncoding.values()) {
         ENCODINGS.put(encoding.getJavaName(), encoding);
      }

      ENCODINGS.put("US-ASCII", JsonEncoding.UTF8);
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
         return AbstractJackson2Encoder.EMPTY_BYTES;
      }

      public byte[] getPrefix() {
         return this.firstItemEmitted ? AbstractJackson2Encoder.EMPTY_BYTES : OPEN_BRACKET;
      }

      public byte[] getSuffix() {
         return CLOSE_BRACKET;
      }
   }
}
