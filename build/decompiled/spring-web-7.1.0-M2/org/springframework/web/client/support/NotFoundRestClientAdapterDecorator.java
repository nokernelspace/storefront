package org.springframework.web.client.support;

import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.service.invoker.HttpExchangeAdapter;
import org.springframework.web.service.invoker.HttpExchangeAdapterDecorator;
import org.springframework.web.service.invoker.HttpRequestValues;

public final class NotFoundRestClientAdapterDecorator extends HttpExchangeAdapterDecorator {
   public NotFoundRestClientAdapterDecorator(HttpExchangeAdapter delegate) {
      super(delegate);
   }

   @Override
   public <T> @Nullable T exchangeForBody(HttpRequestValues values, ParameterizedTypeReference<T> bodyType) {
      try {
         return super.exchangeForBody(values, bodyType);
      } catch (HttpClientErrorException.NotFound ex) {
         return null;
      }
   }

   @Override
   public ResponseEntity<Void> exchangeForBodilessEntity(HttpRequestValues values) {
      try {
         return super.exchangeForBodilessEntity(values);
      } catch (HttpClientErrorException.NotFound ex) {
         return ResponseEntity.notFound().build();
      }
   }

   @Override
   public <T> ResponseEntity<T> exchangeForEntity(HttpRequestValues values, ParameterizedTypeReference<T> bodyType) {
      try {
         return super.exchangeForEntity(values, bodyType);
      } catch (HttpClientErrorException.NotFound ex) {
         return ResponseEntity.notFound().build();
      }
   }
}
