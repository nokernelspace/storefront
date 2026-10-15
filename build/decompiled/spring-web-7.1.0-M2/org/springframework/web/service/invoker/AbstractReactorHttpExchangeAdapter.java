package org.springframework.web.service.invoker;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;

public abstract class AbstractReactorHttpExchangeAdapter implements ReactorHttpExchangeAdapter {
   private ReactiveAdapterRegistry reactiveAdapterRegistry = ReactiveAdapterRegistry.getSharedInstance();
   private @Nullable Duration blockTimeout;

   protected AbstractReactorHttpExchangeAdapter() {
   }

   public void setReactiveAdapterRegistry(ReactiveAdapterRegistry reactiveAdapterRegistry) {
      this.reactiveAdapterRegistry = reactiveAdapterRegistry;
   }

   @Override
   public ReactiveAdapterRegistry getReactiveAdapterRegistry() {
      return this.reactiveAdapterRegistry;
   }

   public void setBlockTimeout(@Nullable Duration blockTimeout) {
      this.blockTimeout = blockTimeout;
   }

   @Override
   public @Nullable Duration getBlockTimeout() {
      return this.blockTimeout;
   }

   @Override
   public void exchange(HttpRequestValues requestValues) {
      if (this.blockTimeout != null) {
         this.exchangeForMono(requestValues).block(this.blockTimeout);
      } else {
         this.exchangeForMono(requestValues).block();
      }
   }

   @Override
   public HttpHeaders exchangeForHeaders(HttpRequestValues requestValues) {
      HttpHeaders headers = this.blockTimeout != null
         ? (HttpHeaders)this.exchangeForHeadersMono(requestValues).block(this.blockTimeout)
         : (HttpHeaders)this.exchangeForHeadersMono(requestValues).block();
      Assert.state(headers != null, "Expected HttpHeaders");
      return headers;
   }

   @Override
   public <T> @Nullable T exchangeForBody(HttpRequestValues requestValues, ParameterizedTypeReference<T> bodyType) {
      return (T)(this.blockTimeout != null
         ? this.exchangeForBodyMono(requestValues, bodyType).block(this.blockTimeout)
         : this.exchangeForBodyMono(requestValues, bodyType).block());
   }

   @Override
   public ResponseEntity<Void> exchangeForBodilessEntity(HttpRequestValues requestValues) {
      ResponseEntity<Void> entity = this.blockTimeout != null
         ? (ResponseEntity)this.exchangeForBodilessEntityMono(requestValues).block(this.blockTimeout)
         : (ResponseEntity)this.exchangeForBodilessEntityMono(requestValues).block();
      Assert.state(entity != null, "Expected ResponseEntity");
      return entity;
   }

   @Override
   public <T> ResponseEntity<T> exchangeForEntity(HttpRequestValues requestValues, ParameterizedTypeReference<T> bodyType) {
      ResponseEntity<T> entity = this.blockTimeout != null
         ? (ResponseEntity)this.exchangeForEntityMono(requestValues, bodyType).block(this.blockTimeout)
         : (ResponseEntity)this.exchangeForEntityMono(requestValues, bodyType).block();
      Assert.state(entity != null, "Expected ResponseEntity");
      return entity;
   }
}
