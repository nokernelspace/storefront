package org.springframework.http;

import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;

public interface ReactiveHttpInputMessage extends HttpMessage {
   Flux<DataBuffer> getBody();
}
