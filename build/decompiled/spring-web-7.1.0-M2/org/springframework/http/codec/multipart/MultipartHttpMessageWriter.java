package org.springframework.http.codec.multipart;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.ResolvableTypeProvider;
import org.springframework.core.codec.CharSequenceEncoder;
import org.springframework.core.codec.CodecException;
import org.springframework.core.codec.Hints;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.codec.EncoderHttpMessageWriter;
import org.springframework.http.codec.FormHttpMessageWriter;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ResourceHttpMessageWriter;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MultipartHttpMessageWriter extends MultipartWriterSupport implements HttpMessageWriter<MultiValueMap<String, ?>> {
   private static final Map<String, Object> DEFAULT_HINTS = Hints.from(Hints.SUPPRESS_LOGGING_HINT, true);
   private final Supplier<List<HttpMessageWriter<?>>> partWritersSupplier;
   private final @Nullable HttpMessageWriter<MultiValueMap<String, String>> formWriter;

   public MultipartHttpMessageWriter() {
      this(Arrays.asList(new EncoderHttpMessageWriter(CharSequenceEncoder.textPlainOnly()), new ResourceHttpMessageWriter()));
   }

   public MultipartHttpMessageWriter(List<HttpMessageWriter<?>> partWriters) {
      this(partWriters, new FormHttpMessageWriter());
   }

   public MultipartHttpMessageWriter(List<HttpMessageWriter<?>> partWriters, @Nullable HttpMessageWriter<MultiValueMap<String, String>> formWriter) {
      this(() -> partWriters, formWriter);
   }

   public MultipartHttpMessageWriter(
      Supplier<List<HttpMessageWriter<?>>> partWritersSupplier, @Nullable HttpMessageWriter<MultiValueMap<String, String>> formWriter
   ) {
      super(initMediaTypes(formWriter));
      this.partWritersSupplier = partWritersSupplier;
      this.formWriter = formWriter;
   }

   private static List<MediaType> initMediaTypes(@Nullable HttpMessageWriter<?> formWriter) {
      List<MediaType> result = new ArrayList<>(MultipartHttpMessageReader.MIME_TYPES);
      if (formWriter != null) {
         result.addAll(formWriter.getWritableMediaTypes());
      }

      return Collections.unmodifiableList(result);
   }

   public List<HttpMessageWriter<?>> getPartWriters() {
      return Collections.unmodifiableList(this.partWritersSupplier.get());
   }

   public @Nullable HttpMessageWriter<MultiValueMap<String, String>> getFormWriter() {
      return this.formWriter;
   }

   @Override
   public boolean canWrite(ResolvableType elementType, @Nullable MediaType mediaType) {
      if (MultiValueMap.class.isAssignableFrom(elementType.toClass())) {
         if (mediaType == null) {
            return true;
         }

         for (MediaType supportedMediaType : this.getWritableMediaTypes()) {
            if (supportedMediaType.isCompatibleWith(mediaType)) {
               return true;
            }
         }
      }

      return false;
   }

   @Override
   public Mono<Void> write(
      Publisher<? extends MultiValueMap<String, ?>> inputStream,
      ResolvableType elementType,
      @Nullable MediaType mediaType,
      ReactiveHttpOutputMessage outputMessage,
      Map<String, Object> hints
   ) {
      return Mono.from(inputStream).flatMap(map -> {
         if (this.formWriter != null && !this.isMultipart(map, mediaType)) {
            Mono<MultiValueMap<String, String>> input = Mono.just(map);
            return this.formWriter.write(input, elementType, mediaType, outputMessage, hints);
         } else {
            return this.writeMultipart(map, outputMessage, mediaType, hints);
         }
      });
   }

   private boolean isMultipart(MultiValueMap<String, ?> map, @Nullable MediaType contentType) {
      if (contentType != null) {
         return contentType.getType().equalsIgnoreCase("multipart");
      }

      for (List<?> values : map.values()) {
         for (Object value : values) {
            if (value != null && !(value instanceof String)) {
               return true;
            }
         }
      }

      return false;
   }

   private Mono<Void> writeMultipart(
      MultiValueMap<String, ?> map, ReactiveHttpOutputMessage outputMessage, @Nullable MediaType mediaType, Map<String, Object> hints
   ) {
      byte[] boundary = this.generateMultipartBoundary();
      mediaType = this.getMultipartMediaType(mediaType, boundary);
      outputMessage.getHeaders().setContentType(mediaType);
      LogFormatUtils.traceDebug(
         this.logger,
         traceOn -> Hints.getLogPrefix(hints)
            + "Encoding "
            + (this.isEnableLoggingRequestDetails() ? LogFormatUtils.formatValue(map, !traceOn) : "parts " + map.keySet() + " (content masked)")
      );
      DataBufferFactory bufferFactory = outputMessage.bufferFactory();
      Flux<DataBuffer> body = Flux.fromIterable(map.entrySet())
         .concatMap(entry -> this.encodePartValues(boundary, (String)entry.getKey(), (List<?>)entry.getValue(), bufferFactory))
         .concatWith(this.generateLastLine(boundary, bufferFactory))
         .doOnDiscard(DataBuffer.class, DataBufferUtils::release);
      if (this.logger.isDebugEnabled()) {
         body = body.doOnNext(buffer -> Hints.touchDataBuffer(buffer, hints, this.logger));
      }

      return outputMessage.writeWith(body);
   }

   private Flux<DataBuffer> encodePartValues(byte[] boundary, String name, List<?> values, DataBufferFactory bufferFactory) {
      return Flux.fromIterable(values).concatMap(value -> this.encodePart(boundary, name, value, bufferFactory));
   }

   private <T> Flux<DataBuffer> encodePart(byte[] boundary, String name, T value, DataBufferFactory factory) {
      MultipartHttpMessageWriter.MultipartHttpOutputMessage message = new MultipartHttpMessageWriter.MultipartHttpOutputMessage(factory);
      HttpHeaders headers = message.getHeaders();
      ResolvableType resolvableType = null;
      T body;
      if (value instanceof HttpEntity httpEntity) {
         headers.putAll(httpEntity.getHeaders());
         body = (T)httpEntity.getBody();
         Assert.state(body != null, "MultipartHttpMessageWriter only supports HttpEntity with body");
         if (httpEntity instanceof ResolvableTypeProvider resolvableTypeProvider) {
            resolvableType = resolvableTypeProvider.getResolvableType();
         }
      } else {
         body = value;
      }

      if (resolvableType == null) {
         resolvableType = ResolvableType.forClass(body.getClass());
      }

      if (!headers.containsHeader("Content-Disposition")) {
         if (body instanceof Resource resource) {
            headers.setContentDispositionFormData(name, resource.getFilename());
         } else if (resolvableType.resolve() == Resource.class) {
            body = (T)Mono.from((Publisher)body).doOnNext(o -> headers.setContentDispositionFormData(name, ((Resource)o).getFilename()));
         } else {
            headers.setContentDispositionFormData(name, null);
         }
      }

      MediaType contentType = headers.getContentType();
      ResolvableType finalBodyType = resolvableType;
      Optional<HttpMessageWriter<?>> writer = this.partWritersSupplier
         .get()
         .stream()
         .filter(partWriter -> partWriter.canWrite(finalBodyType, contentType))
         .findFirst();
      if (writer.isEmpty()) {
         return Flux.error(new CodecException("No suitable writer found for part: " + name));
      }

      Publisher<T> bodyPublisher = (Publisher<T>)(body instanceof Publisher publisher ? publisher : Mono.just(body));
      Mono<Void> partContentReady = writer.get().write(bodyPublisher, resolvableType, contentType, message, DEFAULT_HINTS);
      Flux<DataBuffer> partContent = partContentReady.thenMany(Flux.defer(message::getBody));
      return Flux.concat(new Publisher[]{this.generateBoundaryLine(boundary, factory), partContent, this.generateNewLine(factory)});
   }

   private class MultipartHttpOutputMessage implements ReactiveHttpOutputMessage {
      private final DataBufferFactory bufferFactory;
      private final HttpHeaders headers = new HttpHeaders();
      private final AtomicBoolean committed = new AtomicBoolean();
      private @Nullable Flux<DataBuffer> body;

      public MultipartHttpOutputMessage(DataBufferFactory bufferFactory) {
         this.bufferFactory = bufferFactory;
      }

      @Override
      public HttpHeaders getHeaders() {
         return this.body != null ? HttpHeaders.readOnlyHttpHeaders(this.headers) : this.headers;
      }

      @Override
      public DataBufferFactory bufferFactory() {
         return this.bufferFactory;
      }

      @Override
      public void beforeCommit(Supplier<? extends Mono<Void>> action) {
         this.committed.set(true);
      }

      @Override
      public boolean isCommitted() {
         return this.committed.get();
      }

      @Override
      public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
         if (this.body != null) {
            return Mono.error(new IllegalStateException("Multiple calls to writeWith() not supported"));
         }

         this.body = MultipartHttpMessageWriter.this.generatePartHeaders(this.headers, this.bufferFactory).concatWith(body);
         return Mono.empty();
      }

      @Override
      public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
         return Mono.error(new UnsupportedOperationException());
      }

      public Flux<DataBuffer> getBody() {
         return this.body != null ? this.body : Flux.error(new IllegalStateException("Body has not been written yet"));
      }

      @Override
      public Mono<Void> setComplete() {
         return Mono.error(new UnsupportedOperationException());
      }
   }
}
