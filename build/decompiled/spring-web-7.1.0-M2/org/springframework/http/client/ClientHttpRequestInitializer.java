package org.springframework.http.client;

@FunctionalInterface
public interface ClientHttpRequestInitializer {
   void initialize(ClientHttpRequest request);
}
