package org.springframework.http.converter.smile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.util.Assert;

@Deprecated(since = "7.0", forRemoval = true)
public class MappingJackson2SmileHttpMessageConverter extends AbstractJackson2HttpMessageConverter {
   public MappingJackson2SmileHttpMessageConverter() {
      this(Jackson2ObjectMapperBuilder.smile().build());
   }

   public MappingJackson2SmileHttpMessageConverter(ObjectMapper objectMapper) {
      super(objectMapper, new MediaType("application", "x-jackson-smile"));
      Assert.isInstanceOf(SmileFactory.class, objectMapper.getFactory(), "SmileFactory required");
   }

   @Override
   public void setObjectMapper(ObjectMapper objectMapper) {
      Assert.isInstanceOf(SmileFactory.class, objectMapper.getFactory(), "SmileFactory required");
      super.setObjectMapper(objectMapper);
   }
}
