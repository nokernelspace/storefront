package org.springframework.http.converter.json;

import java.util.function.Predicate;
import kotlinx.serialization.json.Json;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.converter.KotlinSerializationStringHttpMessageConverter;

public class KotlinSerializationJsonHttpMessageConverter extends KotlinSerializationStringHttpMessageConverter<Json> {
   private static final MediaType[] DEFAULT_JSON_MIME_TYPES = new MediaType[]{MediaType.APPLICATION_JSON, new MediaType("application", "*+json")};

   public KotlinSerializationJsonHttpMessageConverter() {
      this(Json.Default);
   }

   public KotlinSerializationJsonHttpMessageConverter(Predicate<ResolvableType> typePredicate) {
      this(Json.Default, typePredicate);
   }

   public KotlinSerializationJsonHttpMessageConverter(Json json) {
      super(json, DEFAULT_JSON_MIME_TYPES);
   }

   public KotlinSerializationJsonHttpMessageConverter(Json json, Predicate<ResolvableType> typePredicate) {
      super(json, typePredicate, DEFAULT_JSON_MIME_TYPES);
   }
}
