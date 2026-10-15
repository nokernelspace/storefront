package org.springframework.web.client;

import java.io.IOException;
import org.springframework.http.client.ClientHttpRequest;

@FunctionalInterface
@Deprecated(since = "7.1", forRemoval = true)
public interface RequestCallback {
   void doWithRequest(ClientHttpRequest request) throws IOException;
}
