package org.springframework.http.converter.support;

import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.multipart.MultipartHttpMessageConverter;

@Deprecated(since = "7.1", forRemoval = true)
public class AllEncompassingFormHttpMessageConverter extends MultipartHttpMessageConverter {
   public AllEncompassingFormHttpMessageConverter() {
      super(HttpMessageConverters.forClient().registerDefaults().build());
   }

   public AllEncompassingFormHttpMessageConverter(Iterable<HttpMessageConverter<?>> converters) {
      super(converters);
   }
}
