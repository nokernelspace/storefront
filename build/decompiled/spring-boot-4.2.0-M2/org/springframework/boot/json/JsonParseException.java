package org.springframework.boot.json;

import org.jspecify.annotations.Nullable;

public class JsonParseException extends IllegalArgumentException {
   public JsonParseException() {
      this(null);
   }

   public JsonParseException(@Nullable Throwable cause) {
      super("Cannot parse JSON", cause);
   }
}
