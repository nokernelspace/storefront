package org.springframework.http.codec.multipart;

import java.io.File;
import java.nio.file.Path;
import reactor.core.publisher.Mono;

public interface FilePart extends Part {
   String filename();

   default Mono<Void> transferTo(File dest) {
      return this.transferTo(dest.toPath());
   }

   Mono<Void> transferTo(Path dest);
}
