package org.springframework.web.service.invoker;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ReactorHttpExchangeAdapterDecorator extends HttpExchangeAdapterDecorator implements ReactorHttpExchangeAdapter {
   public ReactorHttpExchangeAdapterDecorator(HttpExchangeAdapter delegate) {
      super(delegate);
   }

   public ReactorHttpExchangeAdapter getHttpExchangeAdapter() {
      return (ReactorHttpExchangeAdapter)super.getHttpExchangeAdapter();
   }

   @Override
   public boolean supportsRequestAttributes() {
      return this.getHttpExchangeAdapter().supportsRequestAttributes();
   }

   @Override
   public void exchange(HttpRequestValues requestValues) {
      this.getHttpExchangeAdapter().exchange(requestValues);
   }

   @Override
   public HttpHeaders exchangeForHeaders(HttpRequestValues requestValues) {
      return this.getHttpExchangeAdapter().exchangeForHeaders(requestValues);
   }

   @Override
   public <T> @Nullable T exchangeForBody(HttpRequestValues requestValues, ParameterizedTypeReference<T> bodyType) {
      return this.getHttpExchangeAdapter().exchangeForBody(requestValues, bodyType);
   }

   @Override
   public ResponseEntity<Void> exchangeForBodilessEntity(HttpRequestValues requestValues) {
      return this.getHttpExchangeAdapter().exchangeForBodilessEntity(requestValues);
   }

   @Override
   public <T> ResponseEntity<T> exchangeForEntity(HttpRequestValues requestValues, ParameterizedTypeReference<T> bodyType) {
      return this.getHttpExchangeAdapter().exchangeForEntity(requestValues, bodyType);
   }

   @Override
   public ReactiveAdapterRegistry getReactiveAdapterRegistry() {
      return this.getHttpExchangeAdapter().getReactiveAdapterRegistry();
   }

   @Override
   public @Nullable Duration getBlockTimeout() {
      return this.getHttpExchangeAdapter().getBlockTimeout();
   }

   @Override
   public Mono<Void> exchangeForMono(HttpRequestValues requestValues) {
      return this.getHttpExchangeAdapter().exchangeForMono(requestValues);
   }

   @Override
   public Mono<HttpHeaders> exchangeForHeadersMono(HttpRequestValues requestValues) {
      return this.getHttpExchangeAdapter().exchangeForHeadersMono(requestValues);
   }

   @Override
   public <T> Mono<T> exchangeForBodyMono(HttpRequestValues requestValues, ParameterizedTypeReference<T> bodyType) {
      return this.getHttpExchangeAdapter().exchangeForBodyMono(requestValues, bodyType);
   }

   @Override
   public <T> Flux<T> exchangeForBodyFlux(HttpRequestValues requestValues, ParameterizedTypeReference<T> bodyType) {
      return this.getHttpExchangeAdapter().exchangeForBodyFlux(requestValues, bodyType);
   }

   @Override
   public Mono<ResponseEntity<Void>> exchangeForBodilessEntityMono(HttpRequestValues values) {
      return this.getHttpExchangeAdapter().exchangeForBodilessEntityMono(values);
   }

   @Override
   public <T> Mono<ResponseEntity<T>> exchangeForEntityMono(HttpRequestValues values, ParameterizedTypeReference<T> bodyType) {
      return this.getHttpExchangeAdapter().exchangeForEntityMono(values, bodyType);
   }

   @Override
   public <T> Mono<ResponseEntity<Flux<T>>> exchangeForEntityFlux(HttpRequestValues values, ParameterizedTypeReference<T> bodyType) {
      return this.getHttpExchangeAdapter().exchangeForEntityFlux(values, bodyType);
   }
}
