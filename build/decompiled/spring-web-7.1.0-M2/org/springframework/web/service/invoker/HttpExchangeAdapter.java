package org.springframework.web.service.invoker;

import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

public interface HttpExchangeAdapter {
   boolean supportsRequestAttributes();

   void exchange(HttpRequestValues requestValues);

   HttpHeaders exchangeForHeaders(HttpRequestValues requestValues);

   <T> @Nullable T exchangeForBody(HttpRequestValues requestValues, ParameterizedTypeReference<T> bodyType);

   ResponseEntity<Void> exchangeForBodilessEntity(HttpRequestValues requestValues);

   <T> ResponseEntity<T> exchangeForEntity(HttpRequestValues requestValues, ParameterizedTypeReference<T> bodyType);
}
