package com.google.gson;

import com.google.gson.internal.bind.TypeAdapters;

public enum LongSerializationPolicy {
   DEFAULT {
      @Override
      public JsonElement serialize(Long value) {
         return value == null ? JsonNull.INSTANCE : new JsonPrimitive(value);
      }

      @Override
      TypeAdapter<Number> typeAdapter() {
         return TypeAdapters.LONG;
      }
   },
   STRING {
      @Override
      public JsonElement serialize(Long value) {
         return value == null ? JsonNull.INSTANCE : new JsonPrimitive(value.toString());
      }

      @Override
      TypeAdapter<Number> typeAdapter() {
         return TypeAdapters.LONG_AS_STRING;
      }
   };

   LongSerializationPolicy() {
   }

   public abstract JsonElement serialize(Long var1);

   abstract TypeAdapter<Number> typeAdapter();
}
