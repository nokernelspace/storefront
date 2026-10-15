package org.springframework.http.client;

import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

final class BufferingClientHttpRequestWrapper extends AbstractBufferingClientHttpRequest {
   private final ClientHttpRequest request;

   BufferingClientHttpRequestWrapper(ClientHttpRequest request) {
      this.request = request;
   }

   @Override
   public HttpMethod getMethod() {
      return this.request.getMethod();
   }

   @Override
   public URI getURI() {
      return this.request.getURI();
   }

   @Override
   protected ClientHttpResponse executeInternal(HttpHeaders headers, byte[] bufferedOutput) throws IOException {
      this.request.getHeaders().putAll(headers);
      return this.executeWithRequestAndBody(this.request, bufferedOutput, true);
   }
}
