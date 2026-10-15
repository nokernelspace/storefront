package org.springframework.http.client;

import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.util.Assert;

@FunctionalInterface
public interface ClientHttpRequestInterceptor {
   ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException;

   default ClientHttpRequestInterceptor andThen(ClientHttpRequestInterceptor interceptor) {
      Assert.notNull(interceptor, "ClientHttpRequestInterceptor must not be null");
      return (request, body, execution) -> {
         ClientHttpRequestExecution nextExecution = (nextRequest, nextBody) -> interceptor.intercept(nextRequest, nextBody, execution);
         return this.intercept(request, body, nextExecution);
      };
   }

   default ClientHttpRequestExecution apply(ClientHttpRequestExecution execution) {
      Assert.notNull(execution, "ClientHttpRequestExecution must not be null");
      return (request, body) -> this.intercept(request, body, execution);
   }
}
