package org.springframework.boot.json;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public interface JsonParser {
   Map<String, Object> parseMap(@Nullable String json) throws JsonParseException;

   List<Object> parseList(@Nullable String json) throws JsonParseException;
}
