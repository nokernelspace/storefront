package org.springframework.web.service.registry;

import org.springframework.web.service.invoker.HttpExchangeAdapter;

public interface HttpServiceGroupAdapter<CB> {
   CB createClientBuilder();

   Class<? extends HttpServiceGroupConfigurer<CB>> getConfigurerType();

   HttpExchangeAdapter createExchangeAdapter(CB clientBuilder);
}
