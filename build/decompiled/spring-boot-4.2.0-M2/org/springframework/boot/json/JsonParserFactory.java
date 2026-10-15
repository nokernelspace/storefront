package org.springframework.boot.json;

import org.springframework.util.ClassUtils;

public abstract class JsonParserFactory {
   public static JsonParser getJsonParser() {
      if (ClassUtils.isPresent("tools.jackson.databind.ObjectMapper", null)) {
         return new JacksonJsonParser();
      } else {
         return ClassUtils.isPresent("com.google.gson.Gson", null) ? new GsonJsonParser() : new BasicJsonParser();
      }
   }
}
