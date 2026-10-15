package org.springframework.web.client.support;

import org.springframework.web.client.RestClient;
import org.springframework.web.service.invoker.HttpExchangeAdapter;
import org.springframework.web.service.registry.HttpServiceGroupAdapter;
import org.springframework.web.service.registry.HttpServiceGroupConfigurer;

public class RestClientHttpServiceGroupAdapter implements HttpServiceGroupAdapter<RestClient.Builder> {
   public RestClient.Builder createClientBuilder() {
      return RestClient.builder();
   }

   @Override
   public Class<? extends HttpServiceGroupConfigurer<RestClient.Builder>> getConfigurerType() {
      return RestClientHttpServiceGroupConfigurer.class;
   }

   public HttpExchangeAdapter createExchangeAdapter(RestClient.Builder clientBuilder) {
      return RestClientAdapter.create(clientBuilder.build());
   }
}
