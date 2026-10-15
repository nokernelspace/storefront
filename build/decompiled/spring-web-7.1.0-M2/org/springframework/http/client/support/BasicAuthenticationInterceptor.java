package org.springframework.http.client.support;

import java.io.IOException;
import java.nio.charset.Charset;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public class BasicAuthenticationInterceptor implements ClientHttpRequestInterceptor {
   private final String encodedCredentials;

   public BasicAuthenticationInterceptor(String username, String password) {
      this(username, password, null);
   }

   public BasicAuthenticationInterceptor(String username, String password, @Nullable Charset charset) {
      this.encodedCredentials = HttpHeaders.encodeBasicAuth(username, password, charset);
   }

   @Override
   public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
      HttpHeaders headers = request.getHeaders();
      if (!headers.containsHeader("Authorization")) {
         headers.setBasicAuth(this.encodedCredentials);
      }

      return execution.execute(request, body);
   }
}
