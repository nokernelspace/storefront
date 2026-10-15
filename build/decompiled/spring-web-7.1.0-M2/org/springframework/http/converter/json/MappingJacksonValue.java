package org.springframework.http.converter.json;

import com.fasterxml.jackson.databind.ser.FilterProvider;
import org.jspecify.annotations.Nullable;

@Deprecated(since = "7.0", forRemoval = true)
public class MappingJacksonValue {
   private Object value;
   private @Nullable Class<?> serializationView;
   private @Nullable FilterProvider filters;

   public MappingJacksonValue(Object value) {
      this.value = value;
   }

   public void setValue(Object value) {
      this.value = value;
   }

   public Object getValue() {
      return this.value;
   }

   public void setSerializationView(@Nullable Class<?> serializationView) {
      this.serializationView = serializationView;
   }

   public @Nullable Class<?> getSerializationView() {
      return this.serializationView;
   }

   public void setFilters(@Nullable FilterProvider filters) {
      this.filters = filters;
   }

   public @Nullable FilterProvider getFilters() {
      return this.filters;
   }
}
