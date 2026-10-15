package org.springframework.http.codec.multipart;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpInputMessage;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.LoggingCodecSupport;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

public class DefaultPartHttpMessageReader extends LoggingCodecSupport implements HttpMessageReader<Part> {
   private int maxInMemorySize = 262144;
   private int maxHeadersSize = 10240;
   private long maxDiskUsagePerPart = -1L;
   private int maxParts = -1;
   private @Nullable Scheduler blockingOperationScheduler;
   private FileStorage fileStorage = FileStorage.tempDirectory(this::getBlockingOperationScheduler);
   private Charset headersCharset = StandardCharsets.UTF_8;

   public void setMaxHeadersSize(int byteCount) {
      this.maxHeadersSize = byteCount;
   }

   public int getMaxInMemorySize() {
      return this.maxInMemorySize;
   }

   public void setMaxInMemorySize(int maxInMemorySize) {
      this.maxInMemorySize = maxInMemorySize;
   }

   public void setMaxDiskUsagePerPart(long maxDiskUsagePerPart) {
      this.maxDiskUsagePerPart = maxDiskUsagePerPart;
   }

   public void setMaxParts(int maxParts) {
      this.maxParts = maxParts;
   }

   public void setFileStorageDirectory(Path fileStorageDirectory) throws IOException {
      Assert.notNull(fileStorageDirectory, "FileStorageDirectory must not be null");
      this.fileStorage = FileStorage.fromPath(fileStorageDirectory);
   }

   public void setBlockingOperationScheduler(Scheduler blockingOperationScheduler) {
      Assert.notNull(blockingOperationScheduler, "'blockingOperationScheduler' must not be null");
      this.blockingOperationScheduler = blockingOperationScheduler;
   }

   private Scheduler getBlockingOperationScheduler() {
      return this.blockingOperationScheduler != null ? this.blockingOperationScheduler : Schedulers.boundedElastic();
   }

   public void setHeadersCharset(Charset headersCharset) {
      Assert.notNull(headersCharset, "HeadersCharset must not be null");
      this.headersCharset = headersCharset;
   }

   @Override
   public List<MediaType> getReadableMediaTypes() {
      return Collections.singletonList(MediaType.MULTIPART_FORM_DATA);
   }

   @Override
   public boolean canRead(ResolvableType elementType, @Nullable MediaType mediaType) {
      return Part.class.equals(elementType.toClass()) && (mediaType == null || MediaType.MULTIPART_FORM_DATA.isCompatibleWith(mediaType));
   }

   @Override
   public Mono<Part> readMono(ResolvableType elementType, ReactiveHttpInputMessage message, Map<String, Object> hints) {
      return Mono.error(new UnsupportedOperationException("Cannot read multipart request body into single Part"));
   }

   @Override
   public Flux<Part> read(ResolvableType elementType, ReactiveHttpInputMessage message, Map<String, Object> hints) {
      return Flux.defer(
         () -> {
            byte[] boundary = MultipartUtils.boundary(message, this.headersCharset);
            if (boundary == null) {
               return Flux.error(new DecodingException("No multipart boundary found in Content-Type: \"" + message.getHeaders().getContentType() + "\""));
            }

            Flux<MultipartParser.Token> allPartsTokens = MultipartParser.parse(message.getBody(), boundary, this.maxHeadersSize, this.headersCharset);
            AtomicInteger partCount = new AtomicInteger();
            return allPartsTokens.windowUntil(MultipartParser.Token::isLast)
               .concatMap(
                  partsTokens -> this.tooManyParts(partCount)
                     ? partsTokens.doOnNext(token -> {
                        if (token instanceof MultipartParser.BodyToken bodyToken) {
                           DataBufferUtils.release(bodyToken.buffer());
                        }
                     }).then(Mono.error(new DecodingException("Too many parts (" + partCount.get() + "/" + this.maxParts + " allowed)")))
                     : PartGenerator.createPart(
                        partsTokens, this.maxInMemorySize, this.maxDiskUsagePerPart, this.fileStorage.directory(), this.getBlockingOperationScheduler()
                     )
               );
         }
      );
   }

   private boolean tooManyParts(AtomicInteger partCount) {
      int count = partCount.incrementAndGet();
      return this.maxParts > 0 && count > this.maxParts;
   }
}
