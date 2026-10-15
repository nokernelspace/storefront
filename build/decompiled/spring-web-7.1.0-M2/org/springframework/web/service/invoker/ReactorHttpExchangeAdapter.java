package org.springframework.web.service.invoker;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReactorHttpExchangeAdapter extends HttpExchangeAdapter {
   ReactiveAdapterRegistry getReactiveAdapterRegistry();

   @Nullable Duration getBlockTimeout();

   Mono<Void> exchangeForMono(HttpRequestValues requestValues);

   Mono<HttpHeaders> exchangeForHeadersMono(HttpRequestValues requestValues);

   <T> Mono<T> exchangeForBodyMono(HttpRequestValues requestValues, ParameterizedTypeReference<T> bodyType);

   <T> Flux<T> exchangeForBodyFlux(HttpRequestValues requestValues, ParameterizedTypeReference<T> bodyType);

   Mono<ResponseEntity<Void>> exchangeForBodilessEntityMono(HttpRequestValues requestValues);

   <T> Mono<ResponseEntity<T>> exchangeForEntityMono(HttpRequestValues requestValues, ParameterizedTypeReference<T> bodyType);

   <T> Mono<ResponseEntity<Flux<T>>> exchangeForEntityFlux(HttpRequestValues requestValues, ParameterizedTypeReference<T> bodyType);
}
