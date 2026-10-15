package org.springframework.http.server.reactive;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.ReactiveHttpInputMessage;
import org.springframework.http.server.RequestPath;
import org.springframework.util.MultiValueMap;

public interface ServerHttpRequest extends HttpRequest, ReactiveHttpInputMessage {
   String getId();

   RequestPath getPath();

   MultiValueMap<String, String> getQueryParams();

   MultiValueMap<String, HttpCookie> getCookies();

   default @Nullable InetSocketAddress getLocalAddress() {
      return null;
   }

   default @Nullable InetSocketAddress getRemoteAddress() {
      return null;
   }

   default @Nullable SslInfo getSslInfo() {
      return null;
   }

   default ServerHttpRequest.Builder mutate() {
      return new DefaultServerHttpRequestBuilder(this);
   }

   interface Builder {
      ServerHttpRequest.Builder method(HttpMethod httpMethod);

      ServerHttpRequest.Builder uri(URI uri);

      ServerHttpRequest.Builder path(String path);

      ServerHttpRequest.Builder contextPath(@Nullable String contextPath);

      ServerHttpRequest.Builder header(String headerName, String... headerValues);

      ServerHttpRequest.Builder headers(Consumer<HttpHeaders> headersConsumer);

      ServerHttpRequest.Builder sslInfo(SslInfo sslInfo);

      ServerHttpRequest.Builder remoteAddress(InetSocketAddress remoteAddress);

      ServerHttpRequest.Builder localAddress(InetSocketAddress localAddress);

      ServerHttpRequest build();
   }
}
