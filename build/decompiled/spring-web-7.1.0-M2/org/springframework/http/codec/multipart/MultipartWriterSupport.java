package org.springframework.http.codec.multipart;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.LoggingCodecSupport;
import org.springframework.util.Assert;
import org.springframework.util.FastByteArrayOutputStream;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Mono;

public class MultipartWriterSupport extends LoggingCodecSupport {
   public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
   private final List<MediaType> supportedMediaTypes;
   private Charset charset = DEFAULT_CHARSET;

   protected MultipartWriterSupport(List<MediaType> supportedMediaTypes) {
      this.supportedMediaTypes = supportedMediaTypes;
   }

   public Charset getCharset() {
      return this.charset;
   }

   public void setCharset(Charset charset) {
      Assert.notNull(charset, "Charset must not be null");
      this.charset = charset;
   }

   public List<MediaType> getWritableMediaTypes() {
      return this.supportedMediaTypes;
   }

   protected byte[] generateMultipartBoundary() {
      return MimeTypeUtils.generateMultipartBoundary();
   }

   protected MediaType getMultipartMediaType(@Nullable MediaType mediaType, byte[] boundary) {
      Map<String, String> params = new HashMap<>();
      if (mediaType != null) {
         params.putAll(mediaType.getParameters());
      }

      params.put("boundary", new String(boundary, StandardCharsets.US_ASCII));
      Charset charset = this.getCharset();
      if (!charset.equals(StandardCharsets.UTF_8) && !charset.equals(StandardCharsets.US_ASCII)) {
         params.put("charset", charset.name());
      }

      mediaType = mediaType != null ? mediaType : MediaType.MULTIPART_FORM_DATA;
      return new MediaType(mediaType, params);
   }

   protected Mono<DataBuffer> generateBoundaryLine(byte[] boundary, DataBufferFactory bufferFactory) {
      return Mono.fromCallable(() -> {
         DataBuffer buffer = bufferFactory.allocateBuffer(boundary.length + 4);
         buffer.write((byte)45);
         buffer.write((byte)45);
         buffer.write(boundary);
         buffer.write((byte)13);
         buffer.write((byte)10);
         return buffer;
      });
   }

   protected Mono<DataBuffer> generateNewLine(DataBufferFactory bufferFactory) {
      return Mono.fromCallable(() -> {
         DataBuffer buffer = bufferFactory.allocateBuffer(2);
         buffer.write((byte)13);
         buffer.write((byte)10);
         return buffer;
      });
   }

   protected Mono<DataBuffer> generateLastLine(byte[] boundary, DataBufferFactory bufferFactory) {
      return Mono.fromCallable(() -> {
         DataBuffer buffer = bufferFactory.allocateBuffer(boundary.length + 6);
         buffer.write((byte)45);
         buffer.write((byte)45);
         buffer.write(boundary);
         buffer.write((byte)45);
         buffer.write((byte)45);
         buffer.write((byte)13);
         buffer.write((byte)10);
         return buffer;
      });
   }

   protected Mono<DataBuffer> generatePartHeaders(HttpHeaders headers, DataBufferFactory bufferFactory) {
      return Mono.fromCallable(() -> {
         FastByteArrayOutputStream bos = new FastByteArrayOutputStream();

         for (Entry<String, List<String>> entry : headers.headerSet()) {
            byte[] headerName = entry.getKey().getBytes(this.getCharset());

            for (String headerValueString : entry.getValue()) {
               byte[] headerValue = headerValueString.getBytes(this.getCharset());
               bos.write(headerName);
               bos.write(58);
               bos.write(32);
               bos.write(headerValue);
               bos.write(13);
               bos.write(10);
            }
         }

         bos.write(13);
         bos.write(10);
         byte[] bytes = bos.toByteArrayUnsafe();
         return bufferFactory.wrap(bytes);
      });
   }
}
