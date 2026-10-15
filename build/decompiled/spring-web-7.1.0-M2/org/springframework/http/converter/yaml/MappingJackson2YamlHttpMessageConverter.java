package org.springframework.http.converter.yaml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.util.Assert;

@Deprecated(since = "7.0", forRemoval = true)
public class MappingJackson2YamlHttpMessageConverter extends AbstractJackson2HttpMessageConverter {
   public MappingJackson2YamlHttpMessageConverter() {
      this(Jackson2ObjectMapperBuilder.yaml().build());
   }

   public MappingJackson2YamlHttpMessageConverter(ObjectMapper objectMapper) {
      super(objectMapper, MediaType.APPLICATION_YAML);
      Assert.isInstanceOf(YAMLFactory.class, objectMapper.getFactory(), "YAMLFactory required");
   }

   @Override
   public void setObjectMapper(ObjectMapper objectMapper) {
      Assert.isInstanceOf(YAMLFactory.class, objectMapper.getFactory(), "YAMLFactory required");
      super.setObjectMapper(objectMapper);
   }
}
