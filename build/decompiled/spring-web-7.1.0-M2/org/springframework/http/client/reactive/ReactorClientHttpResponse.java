package org.springframework.http.client.reactive;

import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseCookie;
import org.springframework.http.support.Netty4HeadersAdapter;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;
import reactor.core.publisher.Flux;
import reactor.netty.ChannelOperationsId;
import reactor.netty.Connection;
import reactor.netty.NettyInbound;
import reactor.netty.http.client.HttpClientResponse;

class ReactorClientHttpResponse implements ClientHttpResponse {
   private static final Log logger = LogFactory.getLog(ReactorClientHttpResponse.class);
   private final HttpClientResponse response;
   private final HttpHeaders headers;
   private final NettyInbound inbound;
   private final NettyDataBufferFactory bufferFactory;
   private final AtomicInteger state = new AtomicInteger();

   public ReactorClientHttpResponse(HttpClientResponse response, Connection connection) {
      this.response = response;
      MultiValueMap<String, String> adapter = new Netty4HeadersAdapter(response.responseHeaders());
      this.headers = HttpHeaders.readOnlyHttpHeaders(adapter);
      this.inbound = connection.inbound();
      this.bufferFactory = new NettyDataBufferFactory(connection.outbound().alloc());
   }

   @Override
   public String getId() {
      String id = null;
      if (this.response instanceof ChannelOperationsId operationsId) {
         id = logger.isDebugEnabled() ? operationsId.asLongText() : operationsId.asShortText();
      }

      if (id == null && this.response instanceof Connection connection) {
         id = connection.channel().id().asShortText();
      }

      return id != null ? id : ObjectUtils.getIdentityHexString(this);
   }

   @Override
   public Flux<DataBuffer> getBody() {
      return this.inbound.receive().doOnSubscribe(s -> {
         if (!this.state.compareAndSet(0, 1)) {
            if (this.state.get() == 2) {
               throw new IllegalStateException("The client response body has been released already due to cancellation.");
            }
         }
      }).map(byteBuf -> {
         byteBuf.retain();
         return this.bufferFactory.wrap(byteBuf);
      });
   }

   @Override
   public HttpHeaders getHeaders() {
      return this.headers;
   }

   @Override
   public HttpStatusCode getStatusCode() {
      return HttpStatusCode.valueOf(this.response.status().code());
   }

   @Override
   public MultiValueMap<String, ResponseCookie> getCookies() {
      MultiValueMap<String, ResponseCookie> result = new LinkedMultiValueMap();
      this.response
         .cookies()
         .values()
         .stream()
         .flatMap(Collection::stream)
         .forEach(
            cookie -> result.add(
               cookie.name(),
               ResponseCookie.fromClientResponse(cookie.name(), cookie.value())
                  .domain(cookie.domain())
                  .path(cookie.path())
                  .maxAge(cookie.maxAge())
                  .secure(cookie.isSecure())
                  .httpOnly(cookie.isHttpOnly())
                  .sameSite(getSameSite(cookie))
                  .partitioned(getPartitioned(cookie))
                  .build()
            )
         );
      return CollectionUtils.unmodifiableMultiValueMap(result);
   }

   private static @Nullable String getSameSite(Cookie cookie) {
      return cookie instanceof DefaultCookie defaultCookie && defaultCookie.sameSite() != null ? defaultCookie.sameSite().name() : null;
   }

   private static boolean getPartitioned(Cookie cookie) {
      return cookie instanceof DefaultCookie defaultCookie ? defaultCookie.isPartitioned() : false;
   }

   void releaseAfterCancel(HttpMethod method) {
      if (this.mayHaveBody(method) && this.state.compareAndSet(0, 2)) {
         if (logger.isDebugEnabled()) {
            logger.debug("[" + this.getId() + "]Releasing body, not yet subscribed.");
         }

         this.inbound.receive().doOnNext(byteBuf -> {}).subscribe(byteBuf -> {}, ex -> {});
      }
   }

   private boolean mayHaveBody(HttpMethod method) {
      int code = this.getStatusCode().value();
      return (code < 100 || code >= 200) && code != 204 && code != 205 && !method.equals(HttpMethod.HEAD) && this.getHeaders().getContentLength() != 0L;
   }

   @Override
   public String toString() {
      return "ReactorClientHttpResponse{request=[" + this.response.method().name() + " " + this.response.uri() + "],status=" + this.getStatusCode() + "}";
   }
}
