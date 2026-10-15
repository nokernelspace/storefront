package org.springframework.http.converter.xml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.util.Assert;

@Deprecated(since = "7.0", forRemoval = true)
public class MappingJackson2XmlHttpMessageConverter extends AbstractJackson2HttpMessageConverter {
   private static final List<MediaType> problemDetailMediaTypes = Collections.singletonList(MediaType.APPLICATION_PROBLEM_XML);

   public MappingJackson2XmlHttpMessageConverter() {
      this(Jackson2ObjectMapperBuilder.xml().build());
   }

   public MappingJackson2XmlHttpMessageConverter(ObjectMapper objectMapper) {
      super(
         objectMapper,
         new MediaType("application", "xml", StandardCharsets.UTF_8),
         new MediaType("text", "xml", StandardCharsets.UTF_8),
         new MediaType("application", "*+xml", StandardCharsets.UTF_8)
      );
      Assert.isInstanceOf(XmlMapper.class, objectMapper, "XmlMapper required");
   }

   @Override
   public void setObjectMapper(ObjectMapper objectMapper) {
      Assert.isInstanceOf(XmlMapper.class, objectMapper, "XmlMapper required");
      super.setObjectMapper(objectMapper);
   }

   @Override
   protected List<MediaType> getMediaTypesForProblemDetail() {
      return problemDetailMediaTypes;
   }
}
