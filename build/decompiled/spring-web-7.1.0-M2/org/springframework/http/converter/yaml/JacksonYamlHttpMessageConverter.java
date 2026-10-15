package org.springframework.http.converter.yaml;

import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractJacksonHttpMessageConverter;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLMapper.Builder;

public class JacksonYamlHttpMessageConverter extends AbstractJacksonHttpMessageConverter<YAMLMapper> {
   public JacksonYamlHttpMessageConverter() {
      super(YAMLMapper.builder(), MediaType.APPLICATION_YAML);
   }

   public JacksonYamlHttpMessageConverter(Builder builder) {
      super(builder, MediaType.APPLICATION_YAML);
   }

   public JacksonYamlHttpMessageConverter(YAMLMapper mapper) {
      super(mapper, MediaType.APPLICATION_YAML);
   }
}
