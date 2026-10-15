package org.springframework.web.util;

public interface UriBuilderFactory extends UriTemplateHandler {
   UriBuilder uriString(String uriTemplate);

   UriBuilder builder();
}
