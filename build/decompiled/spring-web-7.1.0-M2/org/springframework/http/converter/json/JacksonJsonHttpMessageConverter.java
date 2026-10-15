package org.springframework.http.converter.json;

import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.AbstractJacksonHttpMessageConverter;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.json.JsonMapper.Builder;

public class JacksonJsonHttpMessageConverter extends AbstractJacksonHttpMessageConverter<JsonMapper> {
   private static final List<MediaType> problemDetailMediaTypes = Collections.singletonList(MediaType.APPLICATION_PROBLEM_JSON);
   private static final MediaType[] DEFAULT_JSON_MIME_TYPES = new MediaType[]{MediaType.APPLICATION_JSON, new MediaType("application", "*+json")};
   private @Nullable String jsonPrefix;

   public JacksonJsonHttpMessageConverter() {
      this(JsonMapper.builder());
   }

   public JacksonJsonHttpMessageConverter(Builder builder) {
      super(builder.addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class), DEFAULT_JSON_MIME_TYPES);
   }

   public JacksonJsonHttpMessageConverter(JsonMapper mapper) {
      super(mapper, DEFAULT_JSON_MIME_TYPES);
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
   protected void writePrefix(JsonGenerator generator, Object object) {
      if (this.jsonPrefix != null) {
         generator.writeRaw(this.jsonPrefix);
      }
   }
}
