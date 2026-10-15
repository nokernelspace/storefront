package org.springframework.http.codec.multipart;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface Part {
   String name();

   HttpHeaders headers();

   Flux<DataBuffer> content();

   default Mono<Void> delete() {
      return Mono.empty();
   }
}
