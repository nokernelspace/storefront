package org.springframework.http.converter;

import java.util.List;
import java.util.function.Consumer;

public interface HttpMessageConverters extends Iterable<HttpMessageConverter<?>> {
   boolean isEmpty();

   static HttpMessageConverters.ClientBuilder forClient() {
      return new DefaultHttpMessageConverters.DefaultClientBuilder();
   }

   static HttpMessageConverters.ServerBuilder forServer() {
      return new DefaultHttpMessageConverters.DefaultServerBuilder();
   }

   interface Builder<T extends HttpMessageConverters.Builder<T>> {
      T registerDefaults();

      T disableDefaults();

      T withStringConverter(HttpMessageConverter<?> stringMessageConverter);

      T withFormConverter(HttpMessageConverter<?> formMessageConverter);

      T withKotlinSerializationJsonConverter(HttpMessageConverter<?> kotlinSerializationConverter);

      T withJsonConverter(HttpMessageConverter<?> jsonMessageConverter);

      T withXmlConverter(HttpMessageConverter<?> xmlMessageConverter);

      T withSmileConverter(HttpMessageConverter<?> smileMessageConverter);

      T withKotlinSerializationCborConverter(HttpMessageConverter<?> kotlinSerializationConverter);

      T withCborConverter(HttpMessageConverter<?> cborMessageConverter);

      T withYamlConverter(HttpMessageConverter<?> yamlMessageConverter);

      T addCustomConverter(HttpMessageConverter<?> customConverter);

      T configureMessageConvertersList(Consumer<List<HttpMessageConverter<?>>> configurer);

      T configureMessageConverters(Consumer<HttpMessageConverter<?>> configurer);

      HttpMessageConverters build();
   }

   interface ClientBuilder extends HttpMessageConverters.Builder<HttpMessageConverters.ClientBuilder> {
   }

   interface ServerBuilder extends HttpMessageConverters.Builder<HttpMessageConverters.ServerBuilder> {
   }
}
