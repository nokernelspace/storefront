package org.springframework.http.converter.json;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.util.Base64;

public abstract class GsonBuilderUtils {
   public static GsonBuilder gsonBuilderWithBase64EncodedByteArrays() {
      GsonBuilder builder = new GsonBuilder();
      builder.registerTypeHierarchyAdapter(byte[].class, new GsonBuilderUtils.Base64TypeAdapter());
      return builder;
   }

   private static class Base64TypeAdapter implements JsonSerializer<byte[]>, JsonDeserializer<byte[]> {
      public JsonElement serialize(byte[] src, Type typeOfSrc, JsonSerializationContext context) {
         return new JsonPrimitive(Base64.getEncoder().encodeToString(src));
      }

      public byte[] deserialize(JsonElement json, Type type, JsonDeserializationContext cxt) {
         return Base64.getDecoder().decode(json.getAsString());
      }
   }
}
