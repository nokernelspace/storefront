package org.springframework.boot.json;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

public class JacksonJsonParser extends AbstractJsonParser {
   private static final JacksonJsonParser.MapTypeReference MAP_TYPE = new JacksonJsonParser.MapTypeReference();
   private static final JacksonJsonParser.ListTypeReference LIST_TYPE = new JacksonJsonParser.ListTypeReference();
   private @Nullable JsonMapper jsonMapper;

   public JacksonJsonParser(JsonMapper jsonMapper) {
      this.jsonMapper = jsonMapper;
   }

   public JacksonJsonParser() {
   }

   @Override
   public Map<String, Object> parseMap(@Nullable String json) {
      return this.tryParse(() -> (Map<String, Object>)this.getJsonMapper().readValue(json, MAP_TYPE), Exception.class);
   }

   @Override
   public List<Object> parseList(@Nullable String json) {
      return this.tryParse(() -> (List<Object>)this.getJsonMapper().readValue(json, LIST_TYPE), Exception.class);
   }

   private JsonMapper getJsonMapper() {
      if (this.jsonMapper == null) {
         this.jsonMapper = new JsonMapper();
      }

      return this.jsonMapper;
   }

   private static final class ListTypeReference extends TypeReference<List<Object>> {
   }

   private static final class MapTypeReference extends TypeReference<Map<String, Object>> {
   }
}
