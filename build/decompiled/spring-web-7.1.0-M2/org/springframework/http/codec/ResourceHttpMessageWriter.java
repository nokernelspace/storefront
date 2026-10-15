package org.springframework.http.codec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Hints;
import org.springframework.core.codec.ResourceEncoder;
import org.springframework.core.codec.ResourceRegionEncoder;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpLogging;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.ZeroCopyHttpOutputMessage;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class ResourceHttpMessageWriter implements HttpMessageWriter<Resource> {
   private static final ResolvableType REGION_TYPE = ResolvableType.forClass(ResourceRegion.class);
   private static final Log logger = HttpLogging.forLogName(ResourceHttpMessageWriter.class);
   private final ResourceEncoder encoder;
   private final ResourceRegionEncoder regionEncoder;
   private final List<MediaType> mediaTypes;

   public ResourceHttpMessageWriter() {
      this(8192);
   }

   public ResourceHttpMessageWriter(int bufferSize) {
      this.encoder = new ResourceEncoder(bufferSize);
      this.regionEncoder = new ResourceRegionEncoder(bufferSize);
      this.mediaTypes = MediaType.asMediaTypes(this.encoder.getEncodableMimeTypes());
   }

   @Override
   public boolean canWrite(ResolvableType elementType, @Nullable MediaType mediaType) {
      return this.encoder.canEncode(elementType, mediaType);
   }

   @Override
   public List<MediaType> getWritableMediaTypes() {
      return this.mediaTypes;
   }

   @Override
   public Mono<Void> write(
      Publisher<? extends Resource> inputStream,
      ResolvableType elementType,
      @Nullable MediaType mediaType,
      ReactiveHttpOutputMessage message,
      Map<String, Object> hints
   ) {
      return Mono.from(inputStream).flatMap(resource -> this.writeResource(resource, elementType, mediaType, message, hints));
   }

   private Mono<Void> writeResource(
      Resource resource, ResolvableType type, @Nullable MediaType mediaType, ReactiveHttpOutputMessage message, Map<String, Object> hints
   ) {
      return this.addDefaultHeaders(message, resource, mediaType, hints).then(Mono.defer(() -> {
         Mono<Void> result = zeroCopy(resource, null, message, hints);
         if (result != null) {
            return result;
         }

         Mono<Resource> input = Mono.just(resource);
         DataBufferFactory factory = message.bufferFactory();
         MediaType contentType = message.getHeaders().getContentType();
         Flux<DataBuffer> body = this.encoder.encode(input, factory, type, contentType, hints).subscribeOn(Schedulers.boundedElastic());
         if (logger.isDebugEnabled()) {
            body = body.doOnNext(buffer -> Hints.touchDataBuffer(buffer, hints, logger));
         }

         return message.writeWith(body);
      }));
   }

   public Mono<Void> addDefaultHeaders(ReactiveHttpOutputMessage message, Resource resource, @Nullable MediaType contentType, Map<String, Object> hints) {
      return Mono.defer(() -> {
         HttpHeaders headers = message.getHeaders();
         MediaType resourceMediaType = getResourceMediaType(contentType, resource, hints);
         headers.setContentType(resourceMediaType);
         if (message instanceof ServerHttpResponse) {
            headers.set("Accept-Ranges", "bytes");
         }

         return headers.getContentLength() >= 0L ? Mono.empty() : lengthOf(resource).flatMap(contentLength -> {
            headers.setContentLength(contentLength);
            return Mono.empty();
         });
      });
   }

   private static MediaType getResourceMediaType(@Nullable MediaType mediaType, Resource resource, Map<String, Object> hints) {
      if (mediaType != null && mediaType.isConcrete() && !mediaType.equals(MediaType.APPLICATION_OCTET_STREAM)) {
         return mediaType;
      }

      mediaType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
      if (logger.isDebugEnabled() && !Hints.isLoggingSuppressed(hints)) {
         logger.debug(Hints.getLogPrefix(hints) + "Resource associated with '" + mediaType + "'");
      }

      return mediaType;
   }

   private static Mono<Long> lengthOf(Resource resource) {
      return InputStreamResource.class != resource.getClass()
         ? Mono.fromCallable(resource::contentLength)
            .filter(length -> length != -1L)
            .onErrorComplete(IOException.class)
            .subscribeOn(Schedulers.boundedElastic())
         : Mono.empty();
   }

   private static @Nullable Mono<Void> zeroCopy(
      Resource resource, @Nullable ResourceRegion region, ReactiveHttpOutputMessage message, Map<String, Object> hints
   ) {
      if (message instanceof ZeroCopyHttpOutputMessage zeroCopyHttpOutputMessage && resource.isFile()) {
         try {
            Path filePath = resource.getFilePath();
            long pos = region != null ? region.getPosition() : 0L;
            long count = region != null ? region.getCount() : Files.size(filePath);
            if (logger.isDebugEnabled()) {
               String formatted = region != null ? "region " + pos + "-" + count + " of " : "";
               logger.debug(Hints.getLogPrefix(hints) + "Zero-copy " + formatted + "[" + resource + "]");
            }

            return zeroCopyHttpOutputMessage.writeWith(filePath, pos, count);
         } catch (IOException var11) {
         }
      }

      return null;
   }

   @Override
   public Mono<Void> write(
      Publisher<? extends Resource> inputStream,
      @Nullable ResolvableType actualType,
      ResolvableType elementType,
      @Nullable MediaType mediaType,
      ServerHttpRequest request,
      ServerHttpResponse response,
      Map<String, Object> hints
   ) {
      HttpHeaders headers = response.getHeaders();
      headers.set("Accept-Ranges", "bytes");

      List<HttpRange> ranges;
      try {
         ranges = request.getHeaders().getRange();
      } catch (IllegalArgumentException ex) {
         return handleInvalidRange(response);
      }

      return Mono.from(inputStream).flatMap(resource -> {
         if (ranges.isEmpty()) {
            return this.writeResource(resource, elementType, mediaType, response, hints);
         }

         response.setStatusCode(HttpStatus.PARTIAL_CONTENT);

         List<ResourceRegion> regions;
         try {
            regions = HttpRange.toResourceRegions(ranges, resource);
         } catch (IllegalArgumentException ex) {
            return handleInvalidRange(response);
         }

         MediaType resourceMediaType = getResourceMediaType(mediaType, resource, hints);
         if (regions.size() == 1) {
            ResourceRegion region = regions.get(0);
            headers.setContentType(resourceMediaType);
            return lengthOf(resource).flatMap(contentLength -> {
               long start = region.getPosition();
               long end = start + region.getCount() - 1L;
               end = Math.min(end, contentLength - 1L);
               headers.add("Content-Range", "bytes " + start + "-" + end + "/" + contentLength);
               headers.setContentLength(end - start + 1L);
               return Mono.empty();
            }).then(this.writeSingleRegion(region, response, hints));
         } else {
            String boundary = MimeTypeUtils.generateMultipartBoundaryString();
            MediaType multipartType = MediaType.parseMediaType("multipart/byteranges;boundary=" + boundary);
            headers.setContentType(multipartType);
            Map<String, Object> allHints = Hints.merge(hints, ResourceRegionEncoder.BOUNDARY_STRING_HINT, boundary);
            return this.encodeAndWriteRegions(Flux.fromIterable(regions), resourceMediaType, response, allHints);
         }
      });
   }

   private static Mono<Void> handleInvalidRange(ServerHttpResponse response) {
      response.setStatusCode(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
      return response.setComplete();
   }

   private Mono<Void> writeSingleRegion(ResourceRegion region, ReactiveHttpOutputMessage message, Map<String, Object> hints) {
      Mono<Void> result = zeroCopy(region.getResource(), region, message, hints);
      if (result != null) {
         return result;
      }

      Publisher<? extends ResourceRegion> input = Mono.just(region);
      MediaType mediaType = message.getHeaders().getContentType();
      return this.encodeAndWriteRegions(input, mediaType, message, hints);
   }

   private Mono<Void> encodeAndWriteRegions(
      Publisher<? extends ResourceRegion> publisher, @Nullable MediaType mediaType, ReactiveHttpOutputMessage message, Map<String, Object> hints
   ) {
      Flux<DataBuffer> body = this.regionEncoder
         .encode(publisher, message.bufferFactory(), REGION_TYPE, mediaType, hints)
         .subscribeOn(Schedulers.boundedElastic());
      return message.writeWith(body);
   }
}
