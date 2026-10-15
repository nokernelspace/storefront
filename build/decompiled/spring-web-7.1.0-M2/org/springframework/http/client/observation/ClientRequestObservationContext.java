package org.springframework.http.client.observation;

import io.micrometer.observation.transport.RequestReplySenderContext;
import org.jspecify.annotations.Nullable;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;

public class ClientRequestObservationContext extends RequestReplySenderContext<ClientHttpRequest, ClientHttpResponse> {
   private @Nullable String uriTemplate;

   public ClientRequestObservationContext(ClientHttpRequest request) {
      super(ClientRequestObservationContext::setRequestHeader);
      this.setCarrier(request);
   }

   private static void setRequestHeader(@Nullable ClientHttpRequest request, String name, String value) {
      if (request != null) {
         request.getHeaders().set(name, value);
      }
   }

   public void setUriTemplate(@Nullable String uriTemplate) {
      this.uriTemplate = uriTemplate;
   }

   public @Nullable String getUriTemplate() {
      return this.uriTemplate;
   }
}
