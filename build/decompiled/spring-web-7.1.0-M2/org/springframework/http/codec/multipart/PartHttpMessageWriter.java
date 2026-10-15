package org.springframework.http.codec.multipart;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Hints;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.codec.HttpMessageWriter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PartHttpMessageWriter extends MultipartWriterSupport implements HttpMessageWriter<Part> {
   public PartHttpMessageWriter() {
      super(MultipartHttpMessageReader.MIME_TYPES);
   }

   @Override
   public boolean canWrite(ResolvableType elementType, @Nullable MediaType mediaType) {
      if (Part.class.isAssignableFrom(elementType.toClass())) {
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
      Publisher<? extends Part> parts,
      ResolvableType elementType,
      @Nullable MediaType mediaType,
      ReactiveHttpOutputMessage outputMessage,
      Map<String, Object> hints
   ) {
      byte[] boundary = this.generateMultipartBoundary();
      mediaType = this.getMultipartMediaType(mediaType, boundary);
      outputMessage.getHeaders().setContentType(mediaType);
      if (this.logger.isDebugEnabled()) {
         this.logger.debug(Hints.getLogPrefix(hints) + "Encoding Publisher<Part>");
      }

      Flux<DataBuffer> body = Flux.from(parts)
         .concatMap(part -> this.encodePart(boundary, part, outputMessage.bufferFactory()))
         .concatWith(this.generateLastLine(boundary, outputMessage.bufferFactory()))
         .doOnDiscard(DataBuffer.class, DataBufferUtils::release);
      if (this.logger.isDebugEnabled()) {
         body = body.doOnNext(buffer -> Hints.touchDataBuffer(buffer, hints, this.logger));
      }

      return outputMessage.writeWith(body);
   }

   private <T> Flux<DataBuffer> encodePart(byte[] boundary, Part part, DataBufferFactory bufferFactory) {
      HttpHeaders headers = new HttpHeaders(part.headers());
      String name = part.name();
      if (!headers.containsHeader("Content-Disposition")) {
         headers.setContentDispositionFormData(name, part instanceof FilePart filePart ? filePart.filename() : null);
      }

      return Flux.concat(
         new Publisher[]{
            this.generateBoundaryLine(boundary, bufferFactory),
            this.generatePartHeaders(headers, bufferFactory),
            part.content(),
            this.generateNewLine(bufferFactory)
         }
      );
   }
}
