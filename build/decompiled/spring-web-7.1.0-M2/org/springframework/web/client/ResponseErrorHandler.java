package org.springframework.web.client;

import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;

@Deprecated(since = "7.1", forRemoval = true)
public interface ResponseErrorHandler {
   boolean hasError(ClientHttpResponse response) throws IOException;

   default void handleError(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
   }
}
