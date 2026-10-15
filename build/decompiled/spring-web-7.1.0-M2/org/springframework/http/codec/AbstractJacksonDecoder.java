package org.springframework.http.codec;

import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.CodecException;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.codec.Hints;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.PooledDataBuffer;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.exc.InvalidDefinitionException;
import tools.jackson.databind.util.TokenBuffer;

public abstract class AbstractJacksonDecoder<T extends ObjectMapper> extends JacksonCodecSupport<T> implements HttpMessageDecoder<Object> {
   private int maxInMemorySize = 262144;

   protected AbstractJacksonDecoder(MapperBuilder<T, ?> builder, MimeType... mimeTypes) {
      super(builder, mimeTypes);
   }

   protected AbstractJacksonDecoder(T mapper, MimeType... mimeTypes) {
      super(mapper, mimeTypes);
   }

   public void setMaxInMemorySize(int byteCount) {
      this.maxInMemorySize = byteCount;
   }

   public int getMaxInMemorySize() {
      return this.maxInMemorySize;
   }

   public boolean canDecode(ResolvableType elementType, @Nullable MimeType mimeType) {
      return this.supportsMimeType(mimeType) && this.selectMapper(elementType, mimeType) != null;
   }

   public Flux<Object> decode(Publisher<DataBuffer> input, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {
      T mapper = this.selectMapper(elementType, mimeType);
      if (mapper == null) {
         return Flux.error(new IllegalStateException("No ObjectMapper for " + elementType));
      }

      boolean forceUseOfBigDecimal = mapper.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
      if (BigDecimal.class.equals(elementType.getType())) {
         forceUseOfBigDecimal = true;
      }

      boolean tokenizeArrays = !elementType.isArray() && !Collection.class.isAssignableFrom(elementType.resolve(Object.class));
      Flux<DataBuffer> processed = this.processInput(input, elementType, mimeType, hints);
      Flux<TokenBuffer> tokens = JacksonTokenizer.tokenize(processed, mapper, tokenizeArrays, forceUseOfBigDecimal, this.getMaxInMemorySize());
      return Flux.deferContextual(contextView -> {
         Map<String, Object> hintsToUse = contextView.isEmpty() ? hints : Hints.merge(hints, ContextView.class.getName(), contextView);
         ObjectReader reader = this.createObjectReader(mapper, elementType, hintsToUse);
         return tokens.handle((tokenBuffer, sink) -> {
            try {
               Object value = reader.readValue(tokenBuffer.asParser(this.getMapper()._deserializationContext()));
               this.logValue(value, hints);
               if (value != null) {
                  sink.next(value);
               }
            } catch (JacksonException ex) {
               sink.error(this.processException(ex));
            }
         }).doOnDiscard(PooledDataBuffer.class, DataBufferUtils::release);
      });
   }

   protected Flux<DataBuffer> processInput(
      Publisher<DataBuffer> input, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints
   ) {
      return Flux.from(input);
   }

   public Mono<Object> decodeToMono(Publisher<DataBuffer> input, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {
      return Mono.deferContextual(
         contextView -> {
            Map<String, Object> hintsToUse = contextView.isEmpty() ? hints : Hints.merge(hints, ContextView.class.getName(), contextView);
            return DataBufferUtils.join(input, this.maxInMemorySize)
               .flatMap(dataBuffer -> Mono.justOrEmpty(this.decode(dataBuffer, elementType, mimeType, hintsToUse)));
         }
      );
   }

   public Object decode(DataBuffer dataBuffer, ResolvableType targetType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints) throws DecodingException {
      T mapper = this.selectMapper(targetType, mimeType);
      if (mapper == null) {
         throw new IllegalStateException("No ObjectMapper for " + targetType);
      }

      try {
         ObjectReader objectReader = this.createObjectReader(mapper, targetType, hints);
         Object value = objectReader.readValue(dataBuffer.asInputStream());
         this.logValue(value, hints);
         return value;
      } catch (JacksonException ex) {
         throw this.processException(ex);
      } finally {
         DataBufferUtils.release(dataBuffer);
      }
   }

   private ObjectReader createObjectReader(T mapper, ResolvableType elementType, @Nullable Map<String, Object> hints) {
      Assert.notNull(elementType, "'elementType' must not be null");
      Class<?> contextClass = this.getContextClass(elementType);
      if (contextClass == null && hints != null) {
         contextClass = this.getContextClass((ResolvableType)hints.get(ACTUAL_TYPE_HINT));
      }

      JavaType javaType = this.getJavaType(elementType.getType(), contextClass);
      Class<?> jsonView = hints != null ? (Class)hints.get(JacksonCodecSupport.JSON_VIEW_HINT) : null;
      ObjectReader objectReader = jsonView != null ? mapper.readerWithView(jsonView).forType(javaType) : mapper.readerFor(javaType);
      return this.customizeReader(objectReader, elementType, hints);
   }

   protected ObjectReader customizeReader(ObjectReader reader, ResolvableType elementType, @Nullable Map<String, Object> hints) {
      return reader;
   }

   private @Nullable Class<?> getContextClass(@Nullable ResolvableType elementType) {
      MethodParameter param = elementType != null ? this.getParameter(elementType) : null;
      return param != null ? param.getContainingClass() : null;
   }

   private void logValue(@Nullable Object value, @Nullable Map<String, Object> hints) {
      if (!Hints.isLoggingSuppressed(hints)) {
         LogFormatUtils.traceDebug(this.logger, traceOn -> {
            String formatted = LogFormatUtils.formatValue(value, !traceOn);
            return Hints.getLogPrefix(hints) + "Decoded [" + formatted + "]";
         });
      }
   }

   private CodecException processException(JacksonException ex) {
      if (ex instanceof InvalidDefinitionException ide) {
         JavaType type = ide.getType();
         return new CodecException("Type definition error: " + type, ex);
      } else {
         if (ex instanceof JacksonIOException) {
            return new DecodingException("I/O error while parsing input stream", ex);
         }

         String originalMessage = ex.getOriginalMessage();
         return new DecodingException("JSON decoding error: " + originalMessage, ex);
      }
   }

   @Override
   public Map<String, Object> getDecodeHints(ResolvableType actualType, ResolvableType elementType, ServerHttpRequest request, ServerHttpResponse response) {
      return this.getHints(actualType);
   }

   public List<MimeType> getDecodableMimeTypes() {
      return this.getMimeTypes();
   }

   public List<MimeType> getDecodableMimeTypes(ResolvableType targetType) {
      return this.getMimeTypes(targetType);
   }

   @Override
   protected <A extends Annotation> @Nullable A getAnnotation(MethodParameter parameter, Class<A> annotType) {
      return (A)parameter.getParameterAnnotation(annotType);
   }
}
