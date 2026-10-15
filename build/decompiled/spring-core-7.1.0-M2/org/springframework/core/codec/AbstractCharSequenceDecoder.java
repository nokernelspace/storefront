package org.springframework.core.codec;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.LimitedDataBufferList;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class AbstractCharSequenceDecoder<T extends CharSequence> extends AbstractDataBufferDecoder<T> {
   public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
   public static final List<String> DEFAULT_DELIMITERS = List.of("\r\n", "\n");
   private final List<String> delimiters;
   private final boolean stripDelimiter;
   private Charset defaultCharset = DEFAULT_CHARSET;
   private final ConcurrentMap<Charset, byte[][]> delimitersCache = new ConcurrentHashMap<>();

   protected AbstractCharSequenceDecoder(List<String> delimiters, boolean stripDelimiter, MimeType... mimeTypes) {
      super(mimeTypes);
      Assert.notEmpty(delimiters, "'delimiters' must not be empty");
      this.delimiters = new ArrayList<>(delimiters);
      this.stripDelimiter = stripDelimiter;
   }

   public void setDefaultCharset(Charset defaultCharset) {
      this.defaultCharset = defaultCharset;
   }

   public Charset getDefaultCharset() {
      return this.defaultCharset;
   }

   @Override
   public final Flux<T> decode(Publisher<DataBuffer> input, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {
      byte[][] delimiterBytes = this.getDelimiterBytes(mimeType);
      LimitedDataBufferList chunks = new LimitedDataBufferList(this.getMaxInMemorySize());
      DataBufferUtils.Matcher matcher = DataBufferUtils.matcher(delimiterBytes);
      return Flux.from(input)
         .concatMapIterable(buffer -> this.processDataBuffer(buffer, matcher, chunks))
         .concatWith(Mono.defer(() -> {
            if (chunks.isEmpty()) {
               return Mono.empty();
            }

            DataBuffer lastBuffer = chunks.get(0).factory().join(chunks);
            chunks.clear();
            return Mono.just(lastBuffer);
         }))
         .doFinally(signalType -> chunks.releaseAndClear())
         .doOnDiscard(DataBuffer.class, DataBufferUtils::release)
         .map(buffer -> this.decode(buffer, elementType, mimeType, hints));
   }

   private byte[][] getDelimiterBytes(@Nullable MimeType mimeType) {
      return this.delimitersCache.computeIfAbsent(this.getCharset(mimeType), charset -> {
         byte[][] result = new byte[this.delimiters.size()][];

         for (int i = 0; i < this.delimiters.size(); i++) {
            result[i] = this.delimiters.get(i).getBytes(charset);
         }

         return result;
      });
   }

   private Collection<DataBuffer> processDataBuffer(DataBuffer buffer, DataBufferUtils.Matcher matcher, LimitedDataBufferList chunks) {
      boolean release = true;

      try {
         List<DataBuffer> result = null;

         do {
            int endIndex = matcher.match(buffer);
            if (endIndex == -1) {
               chunks.add(buffer);
               release = false;
               break;
            }

            DataBuffer split = buffer.split(endIndex + 1);
            if (result == null) {
               result = new ArrayList<>();
            }

            int delimiterLength = matcher.delimiter().length;
            if (chunks.isEmpty()) {
               if (this.stripDelimiter) {
                  split.writePosition(split.writePosition() - delimiterLength);
               }

               result.add(split);
            } else {
               chunks.add(split);
               DataBuffer joined = buffer.factory().join(chunks);
               if (this.stripDelimiter) {
                  joined.writePosition(joined.writePosition() - delimiterLength);
               }

               result.add(joined);
               chunks.clear();
            }
         } while (buffer.readableByteCount() > 0);

         return result != null ? result : Collections.emptyList();
      } finally {
         if (release) {
            DataBufferUtils.release(buffer);
         }
      }
   }

   public final T decode(DataBuffer dataBuffer, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {
      try {
         Charset charset = this.getCharset(mimeType);
         T value = this.decodeInternal(dataBuffer, charset);
         LogFormatUtils.traceDebug(this.logger, traceOn -> {
            String formatted = LogFormatUtils.formatValue(value, !traceOn);
            return Hints.getLogPrefix(hints) + "Decoded " + formatted;
         });
         return value;
      } finally {
         DataBufferUtils.release(dataBuffer);
      }
   }

   private Charset getCharset(@Nullable MimeType mimeType) {
      if (mimeType != null) {
         Charset charset = mimeType.getCharset();
         if (charset != null) {
            return charset;
         }
      }

      return this.getDefaultCharset();
   }

   protected abstract T decodeInternal(DataBuffer dataBuffer, Charset charset);
}
