package org.springframework.web.client;

import java.io.IOException;
import org.springframework.http.client.ClientHttpResponse;

@Deprecated(since = "7.1", forRemoval = true)
public final class NoOpResponseErrorHandler implements ResponseErrorHandler {
   @Override
   public boolean hasError(ClientHttpResponse response) throws IOException {
      return false;
   }
}
