package org.springframework.http.converter.json;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;

public class JsonbHttpMessageConverter extends AbstractJsonHttpMessageConverter {
   private Jsonb jsonb;

   public JsonbHttpMessageConverter() {
      this(JsonbBuilder.create());
   }

   public JsonbHttpMessageConverter(JsonbConfig config) {
      this.jsonb = JsonbBuilder.create(config);
   }

   public JsonbHttpMessageConverter(Jsonb jsonb) {
      Assert.notNull(jsonb, "A Jsonb instance is required");
      this.jsonb = jsonb;
   }

   public void setJsonb(Jsonb jsonb) {
      Assert.notNull(jsonb, "A Jsonb instance is required");
      this.jsonb = jsonb;
   }

   public Jsonb getJsonb() {
      return this.jsonb;
   }

   @Override
   protected Object readInternal(Type resolvedType, Reader reader) throws Exception {
      return this.getJsonb().fromJson(reader, resolvedType);
   }

   @Override
   protected void writeInternal(Object object, @Nullable Type type, Writer writer) throws Exception {
      if (type instanceof ParameterizedType) {
         this.getJsonb().toJson(object, type, writer);
      } else {
         this.getJsonb().toJson(object, writer);
      }
   }

   @Override
   public boolean canWriteRepeatedly(Object o, @Nullable MediaType contentType) {
      return true;
   }

   @Override
   protected boolean supportsRepeatableWrites(Object o) {
      return true;
   }
}
