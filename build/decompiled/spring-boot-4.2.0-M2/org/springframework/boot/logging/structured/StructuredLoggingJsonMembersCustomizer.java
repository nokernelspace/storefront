package org.springframework.boot.logging.structured;

import org.springframework.boot.json.JsonWriter;

@FunctionalInterface
public interface StructuredLoggingJsonMembersCustomizer<T> {
   void customize(JsonWriter.Members<T> members);

   interface Builder<T> {
      default StructuredLoggingJsonMembersCustomizer.Builder<T> nested() {
         return this.nested(true);
      }

      StructuredLoggingJsonMembersCustomizer.Builder<T> nested(boolean nested);

      StructuredLoggingJsonMembersCustomizer<T> build();
   }
}
