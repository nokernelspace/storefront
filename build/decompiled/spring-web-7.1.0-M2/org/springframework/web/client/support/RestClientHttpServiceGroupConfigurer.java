package org.springframework.web.client.support;

import org.springframework.web.client.RestClient;
import org.springframework.web.service.registry.HttpServiceGroupConfigurer;

public interface RestClientHttpServiceGroupConfigurer extends HttpServiceGroupConfigurer<RestClient.Builder> {
}
