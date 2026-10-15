package org.springframework.http.codec.multipart;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpInputMessage;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.LoggingCodecSupport;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PartEventHttpMessageReader extends LoggingCodecSupport implements HttpMessageReader<PartEvent> {
   private int maxInMemorySize = 262144;
   private int maxHeadersSize = 10240;
   private int maxParts = -1;
   private long maxPartSize = -1L;
   private Charset headersCharset = StandardCharsets.UTF_8;

   public int getMaxInMemorySize() {
      return this.maxInMemorySize;
   }

   public void setMaxInMemorySize(int maxInMemorySize) {
      this.maxInMemorySize = maxInMemorySize;
   }

   public void setMaxHeadersSize(int byteCount) {
      this.maxHeadersSize = byteCount;
   }

   public void setMaxParts(int maxParts) {
      this.maxParts = maxParts;
   }

   public void setMaxPartSize(long maxPartSize) {
      this.maxPartSize = maxPartSize;
   }

   public void setHeadersCharset(Charset headersCharset) {
      Assert.notNull(headersCharset, "Charset must not be null");
      this.headersCharset = headersCharset;
   }

   @Override
   public List<MediaType> getReadableMediaTypes() {
      return Collections.singletonList(MediaType.MULTIPART_FORM_DATA);
   }

   @Override
   public boolean canRead(ResolvableType elementType, @Nullable MediaType mediaType) {
      return PartEvent.class.equals(elementType.toClass()) && (mediaType == null || MediaType.MULTIPART_FORM_DATA.isCompatibleWith(mediaType));
   }

   @Override
   public Mono<PartEvent> readMono(ResolvableType elementType, ReactiveHttpInputMessage message, Map<String, Object> hints) {
      return Mono.error(new UnsupportedOperationException("Cannot read multipart request body into single PartEvent"));
   }

   @Override
   public Flux<PartEvent> read(ResolvableType elementType, ReactiveHttpInputMessage message, Map<String, Object> hints) {
      return Flux.defer(
         () -> {
            byte[] boundary = MultipartUtils.boundary(message, this.headersCharset);
            if (boundary == null) {
               return Flux.error(new DecodingException("No multipart boundary found in Content-Type: \"" + message.getHeaders().getContentType() + "\""));
            }

            Flux<MultipartParser.Token> allPartsTokens = MultipartParser.parse(message.getBody(), boundary, this.maxHeadersSize, this.headersCharset);
            AtomicInteger partCount = new AtomicInteger();
            return allPartsTokens.windowUntil(MultipartParser.HeadersToken.class::isInstance, true)
               .concatMap(partTokens -> partTokens.switchOnFirst((signal, flux) -> {
                  if (!signal.hasValue()) {
                     return flux.cast(PartEvent.class);
                  }

                  if (this.tooManyParts(partCount)) {
                     return Mono.error(new DecodingException("Too many parts (" + partCount.get() + "/" + this.maxParts + " allowed)"));
                  }

                  MultipartParser.HeadersToken headersToken = (MultipartParser.HeadersToken)signal.get();
                  Assert.state(headersToken != null, "Signal should be headers token");
                  HttpHeaders headers = headersToken.headers();
                  return this.createEvents(headers, flux.ofType(MultipartParser.BodyToken.class));
               }));
         }
      );
   }

   private boolean tooManyParts(AtomicInteger partCount) {
      int count = partCount.incrementAndGet();
      return this.maxParts > 0 && count > this.maxParts;
   }

   private Publisher<? extends PartEvent> createEvents(HttpHeaders headers, Flux<MultipartParser.BodyToken> bodyTokens) {
      if (MultipartUtils.isFormField(headers)) {
         Flux<DataBuffer> contents = bodyTokens.map(MultipartParser.BodyToken::buffer);
         int maxSize;
         if (this.maxPartSize == -1L) {
            maxSize = this.maxInMemorySize;
         } else if (this.maxInMemorySize == -1) {
            maxSize = (int)Math.min(2147483647L, this.maxPartSize);
         } else {
            maxSize = (int)Math.min(this.maxInMemorySize, this.maxPartSize);
         }

         return DataBufferUtils.join(contents, maxSize).map(content -> {
            String value = content.toString(MultipartUtils.charset(headers));
            DataBufferUtils.release(content);
            return DefaultPartEvents.form(headers, value);
         }).switchIfEmpty(Mono.fromCallable(() -> DefaultPartEvents.form(headers)));
      } else {
         boolean isFilePart = headers.getContentDisposition().getFilename() != null;
         AtomicLong partSize = new AtomicLong();
         return bodyTokens.concatMap(
               body -> {
                  DataBuffer buffer = body.buffer();
                  if (this.tooLarge(partSize, buffer)) {
                     DataBufferUtils.release(buffer);
                     return Mono.error(new DataBufferLimitException("Part exceeded the limit of " + this.maxPartSize + " bytes"));
                  } else {
                     return isFilePart
                        ? Mono.just(DefaultPartEvents.file(headers, buffer, body.isLast()))
                        : Mono.just(DefaultPartEvents.create(headers, body.buffer(), body.isLast()));
                  }
               }
            )
            .switchIfEmpty(Mono.fromCallable(() -> isFilePart ? DefaultPartEvents.file(headers) : DefaultPartEvents.create(headers)));
      }
   }

   private boolean tooLarge(AtomicLong partSize, DataBuffer buffer) {
      if (this.maxPartSize != -1L) {
         long size = partSize.addAndGet(buffer.readableByteCount());
         return size > this.maxPartSize;
      } else {
         return false;
      }
   }
}
