package org.springframework.web.client;

import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.http.client.ClientHttpResponse;

@FunctionalInterface
@Deprecated(since = "7.1", forRemoval = true)
public interface ResponseExtractor<T> {
   @Nullable T extractData(ClientHttpResponse response) throws IOException;
}
