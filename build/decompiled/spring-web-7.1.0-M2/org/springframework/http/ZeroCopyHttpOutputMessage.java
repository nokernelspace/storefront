package org.springframework.http;

import java.io.File;
import java.nio.file.Path;
import reactor.core.publisher.Mono;

public interface ZeroCopyHttpOutputMessage extends ReactiveHttpOutputMessage {
   default Mono<Void> writeWith(File file, long position, long count) {
      return this.writeWith(file.toPath(), position, count);
   }

   Mono<Void> writeWith(Path file, long position, long count);
}
