package org.springframework.http.server.reactive;

import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URISyntaxException;
import java.util.function.BiFunction;
import org.apache.commons.logging.Log;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.HttpLogging;
import org.springframework.http.HttpMethod;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

public class ReactorHttpHandlerAdapter implements BiFunction<HttpServerRequest, HttpServerResponse, Mono<Void>> {
   private static final Log logger = HttpLogging.forLogName(ReactorHttpHandlerAdapter.class);
   private final HttpHandler httpHandler;

   public ReactorHttpHandlerAdapter(HttpHandler httpHandler) {
      Assert.notNull(httpHandler, "HttpHandler must not be null");
      this.httpHandler = httpHandler;
   }

   public Mono<Void> apply(HttpServerRequest reactorRequest, HttpServerResponse reactorResponse) {
      NettyDataBufferFactory bufferFactory = new NettyDataBufferFactory(reactorResponse.alloc());

      try {
         ReactorServerHttpRequest request = new ReactorServerHttpRequest(reactorRequest, bufferFactory);
         ServerHttpResponse response = new ReactorServerHttpResponse(reactorResponse, bufferFactory);
         if (request.getMethod() == HttpMethod.HEAD) {
            response = new HttpHeadResponseDecorator(response);
         }

         return this.httpHandler
            .handle(request, response)
            .doOnError(exx -> logger.trace(request.getLogPrefix() + "Failed to complete: " + exx.getMessage()))
            .doOnSuccess(aVoid -> logger.trace(request.getLogPrefix() + "Handling completed"));
      } catch (URISyntaxException ex) {
         if (logger.isDebugEnabled()) {
            logger.debug("Failed to get request URI: " + ex.getMessage());
         }

         reactorResponse.status(HttpResponseStatus.BAD_REQUEST);
         return Mono.empty();
      }
   }
}
