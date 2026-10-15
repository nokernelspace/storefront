package org.springframework.http.client.reactive;

import org.eclipse.jetty.reactive.client.ReactiveResponse;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseCookie;
import org.springframework.http.support.JettyHeadersAdapter;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;

class JettyClientHttpResponse extends AbstractClientHttpResponse {
   public JettyClientHttpResponse(ReactiveResponse response, Flux<DataBuffer> content, MultiValueMap<String, ResponseCookie> cookies) {
      super(HttpStatusCode.valueOf(response.getStatus()), adaptHeaders(response), cookies, content);
   }

   private static HttpHeaders adaptHeaders(ReactiveResponse response) {
      MultiValueMap<String, String> headers = new JettyHeadersAdapter(response.getHeaders());
      return HttpHeaders.readOnlyHttpHeaders(headers);
   }
}
