package org.springframework.http.client;

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCounted;
import java.io.IOException;
import java.io.InputStream;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.FlowAdapters;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.support.Netty4HeadersAdapter;
import org.springframework.util.StreamUtils;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClientResponse;

final class ReactorClientHttpResponse implements ClientHttpResponse {
   private final HttpClientResponse response;
   private final Connection connection;
   private final HttpHeaders headers;
   private volatile @Nullable InputStream body;

   public ReactorClientHttpResponse(HttpClientResponse response, Connection connection) {
      this.response = response;
      this.connection = connection;
      this.headers = HttpHeaders.readOnlyHttpHeaders(new Netty4HeadersAdapter(response.responseHeaders()));
   }

   @Override
   public HttpStatusCode getStatusCode() {
      return HttpStatusCode.valueOf(this.response.status().code());
   }

   @Override
   public String getStatusText() {
      return this.response.status().reasonPhrase();
   }

   @Override
   public HttpHeaders getHeaders() {
      return this.headers;
   }

   @Override
   public InputStream getBody() throws IOException {
      InputStream body = this.body;
      if (body != null) {
         return body;
      }

      try {
         SubscriberInputStream<ByteBuf> is = new SubscriberInputStream<>(byteBuf -> {
            byte[] bytes = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(bytes);
            byteBuf.release();
            return bytes;
         }, ReferenceCounted::release, 16);
         this.connection.inbound().receive().retain().subscribe(FlowAdapters.toSubscriber(is));
         this.body = is;
         return is;
      } catch (RuntimeException ex) {
         throw ReactorClientHttpRequest.convertException(ex);
      }
   }

   @Override
   public void close() {
      try {
         InputStream body = this.getBody();
         StreamUtils.drain(body);
         body.close();
      } catch (IOException var2) {
      }
   }
}
