package org.springframework.http.converter.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;

@Deprecated(since = "7.0", forRemoval = true)
public class MappingJackson2HttpMessageConverter extends AbstractJackson2HttpMessageConverter {
   private static final List<MediaType> problemDetailMediaTypes = Collections.singletonList(MediaType.APPLICATION_PROBLEM_JSON);
   private @Nullable String jsonPrefix;

   public MappingJackson2HttpMessageConverter() {
      this(Jackson2ObjectMapperBuilder.json().build());
   }

   public MappingJackson2HttpMessageConverter(ObjectMapper objectMapper) {
      super(objectMapper, MediaType.APPLICATION_JSON, new MediaType("application", "*+json"));
   }

   public void setJsonPrefix(String jsonPrefix) {
      this.jsonPrefix = jsonPrefix;
   }

   public void setPrefixJson(boolean prefixJson) {
      this.jsonPrefix = prefixJson ? ")]}', " : null;
   }

   @Override
   protected List<MediaType> getMediaTypesForProblemDetail() {
      return problemDetailMediaTypes;
   }

   @Override
   protected void writePrefix(JsonGenerator generator, Object object) throws IOException {
      if (this.jsonPrefix != null) {
         generator.writeRaw(this.jsonPrefix);
      }
   }
}
