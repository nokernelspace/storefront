package org.springframework.http.converter.json;

import com.google.gson.Gson;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;

public class GsonHttpMessageConverter extends AbstractJsonHttpMessageConverter {
   private Gson gson;

   public GsonHttpMessageConverter() {
      this.gson = new Gson();
   }

   public GsonHttpMessageConverter(Gson gson) {
      Assert.notNull(gson, "A Gson instance is required");
      this.gson = gson;
   }

   public void setGson(Gson gson) {
      Assert.notNull(gson, "A Gson instance is required");
      this.gson = gson;
   }

   public Gson getGson() {
      return this.gson;
   }

   @Override
   protected Object readInternal(Type resolvedType, Reader reader) throws Exception {
      return this.getGson().fromJson(reader, resolvedType);
   }

   @Override
   protected void writeInternal(Object object, @Nullable Type type, Writer writer) throws Exception {
      if (type instanceof ParameterizedType) {
         this.getGson().toJson(object, type, writer);
      } else {
         this.getGson().toJson(object, writer);
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
