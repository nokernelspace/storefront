package org.springframework.http.server.reactive;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.io.Content.Source;
import org.eclipse.jetty.io.EndPoint.SslSessionData;
import org.eclipse.jetty.server.Request;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.FlowAdapters;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.JettyDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.support.JettyHeadersAdapter;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;

class JettyCoreServerHttpRequest extends AbstractServerHttpRequest {
   private final JettyDataBufferFactory dataBufferFactory;
   private final Request request;

   public JettyCoreServerHttpRequest(Request request, JettyDataBufferFactory dataBufferFactory) {
      super(
         HttpMethod.valueOf(request.getMethod()),
         request.getHttpURI().toURI(),
         request.getContext().getContextPath(),
         new HttpHeaders(new JettyHeadersAdapter(request.getHeaders()))
      );
      this.dataBufferFactory = dataBufferFactory;
      this.request = request;
   }

   @Override
   protected MultiValueMap<String, org.springframework.http.HttpCookie> initCookies() {
      List<HttpCookie> httpCookies = Request.getCookies(this.request);
      if (httpCookies.isEmpty()) {
         return CollectionUtils.toMultiValueMap(Collections.emptyMap());
      }

      MultiValueMap<String, org.springframework.http.HttpCookie> cookies = new LinkedMultiValueMap();

      for (HttpCookie c : httpCookies) {
         cookies.add(c.getName(), new org.springframework.http.HttpCookie(c.getName(), c.getValue()));
      }

      return cookies;
   }

   @Override
   public @Nullable SslInfo initSslInfo() {
      return this.request.getConnectionMetaData().isSecure()
            && this.request.getAttribute("org.eclipse.jetty.io.Endpoint.SslSessionData") instanceof SslSessionData sessionData
         ? new DefaultSslInfo(sessionData.sslSessionId(), sessionData.peerCertificates())
         : null;
   }

   @Override
   public <T> T getNativeRequest() {
      return (T)this.request;
   }

   @Override
   protected String initId() {
      return this.request.getId();
   }

   @Override
   public @Nullable InetSocketAddress getLocalAddress() {
      return this.request.getConnectionMetaData().getLocalSocketAddress() instanceof InetSocketAddress inet ? inet : null;
   }

   @Override
   public @Nullable InetSocketAddress getRemoteAddress() {
      return this.request.getConnectionMetaData().getRemoteSocketAddress() instanceof InetSocketAddress inet ? inet : null;
   }

   @Override
   public Flux<DataBuffer> getBody() {
      return Flux.from(FlowAdapters.toPublisher(Source.asPublisher(this.request))).map(chunk -> {
         chunk.retain();
         return this.dataBufferFactory.wrap(chunk);
      });
   }
}
