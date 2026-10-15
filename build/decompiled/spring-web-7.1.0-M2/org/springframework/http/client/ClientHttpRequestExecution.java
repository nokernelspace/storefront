package org.springframework.http.client;

import java.io.IOException;
import org.springframework.http.HttpRequest;

@FunctionalInterface
public interface ClientHttpRequestExecution {
   ClientHttpResponse execute(HttpRequest request, byte[] body) throws IOException;
}
