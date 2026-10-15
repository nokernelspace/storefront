package org.springframework.core.codec;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface Decoder<T> {
   boolean canDecode(ResolvableType elementType, @Nullable MimeType mimeType);

   Flux<T> decode(Publisher<DataBuffer> inputStream, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints);

   Mono<T> decodeToMono(Publisher<DataBuffer> inputStream, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints);

   default T decode(DataBuffer buffer, ResolvableType targetType, MimeType mimeType, Map<String, Object> hints) throws DecodingException {
      CompletableFuture<T> future = this.decodeToMono(Mono.just(buffer), targetType, mimeType, hints).toFuture();
      Assert.state(future.isDone(), "DataBuffer decoding should have completed");

      try {
         return future.get();
      } catch (ExecutionException ex) {
         Throwable cause = ex.getCause();
         throw cause instanceof CodecException codecException
            ? codecException
            : new DecodingException("Failed to decode: " + (cause != null ? cause.getMessage() : ex), cause);
      } catch (InterruptedException ex) {
         throw new DecodingException("Interrupted during decode", ex);
      }
   }

   List<MimeType> getDecodableMimeTypes();

   default List<MimeType> getDecodableMimeTypes(ResolvableType targetType) {
      return this.canDecode(targetType, null) ? this.getDecodableMimeTypes() : Collections.emptyList();
   }
}
