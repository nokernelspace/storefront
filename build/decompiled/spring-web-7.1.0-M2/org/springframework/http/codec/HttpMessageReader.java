package org.springframework.http.codec;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpInputMessage;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface HttpMessageReader<T> {
   List<MediaType> getReadableMediaTypes();

   default List<MediaType> getReadableMediaTypes(ResolvableType elementType) {
      return this.canRead(elementType, null) ? this.getReadableMediaTypes() : Collections.emptyList();
   }

   boolean canRead(ResolvableType elementType, @Nullable MediaType mediaType);

   Flux<T> read(ResolvableType elementType, ReactiveHttpInputMessage message, Map<String, Object> hints);

   Mono<T> readMono(ResolvableType elementType, ReactiveHttpInputMessage message, Map<String, Object> hints);

   default Flux<T> read(
      ResolvableType actualType, ResolvableType elementType, ServerHttpRequest request, ServerHttpResponse response, Map<String, Object> hints
   ) {
      return this.read(elementType, request, hints);
   }

   default Mono<T> readMono(
      ResolvableType actualType, ResolvableType elementType, ServerHttpRequest request, ServerHttpResponse response, Map<String, Object> hints
   ) {
      return this.readMono(elementType, request, hints);
   }
}
