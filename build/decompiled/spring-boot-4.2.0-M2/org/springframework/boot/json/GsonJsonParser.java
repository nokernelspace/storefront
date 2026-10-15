package org.springframework.boot.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public class GsonJsonParser extends AbstractJsonParser {
   private static final TypeToken<?> MAP_TYPE = new GsonJsonParser.MapTypeToken();
   private static final TypeToken<?> LIST_TYPE = new GsonJsonParser.ListTypeToken();
   private final Gson gson = new GsonBuilder().create();

   @Override
   public Map<String, Object> parseMap(@Nullable String json) {
      return this.tryParse(() -> this.parseMap(json, trimmed -> (Map<String, Object>)this.gson.fromJson(trimmed, MAP_TYPE.getType())), Exception.class);
   }

   @Override
   public List<Object> parseList(@Nullable String json) {
      return this.tryParse(() -> this.parseList(json, trimmed -> (List<Object>)this.gson.fromJson(trimmed, LIST_TYPE.getType())), Exception.class);
   }

   private static final class ListTypeToken extends TypeToken<List<Object>> {
   }

   private static final class MapTypeToken extends TypeToken<Map<String, Object>> {
   }
}
