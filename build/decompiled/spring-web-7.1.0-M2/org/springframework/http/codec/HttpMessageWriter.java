package org.springframework.http.codec;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Mono;

public interface HttpMessageWriter<T> {
   List<MediaType> getWritableMediaTypes();

   default List<MediaType> getWritableMediaTypes(ResolvableType elementType) {
      return this.canWrite(elementType, null) ? this.getWritableMediaTypes() : Collections.emptyList();
   }

   boolean canWrite(ResolvableType elementType, @Nullable MediaType mediaType);

   Mono<Void> write(
      Publisher<? extends T> inputStream,
      ResolvableType elementType,
      @Nullable MediaType mediaType,
      ReactiveHttpOutputMessage message,
      Map<String, Object> hints
   );

   default Mono<Void> write(
      Publisher<? extends T> inputStream,
      ResolvableType actualType,
      ResolvableType elementType,
      @Nullable MediaType mediaType,
      ServerHttpRequest request,
      ServerHttpResponse response,
      Map<String, Object> hints
   ) {
      return this.write(inputStream, elementType, mediaType, response, hints);
   }
}
