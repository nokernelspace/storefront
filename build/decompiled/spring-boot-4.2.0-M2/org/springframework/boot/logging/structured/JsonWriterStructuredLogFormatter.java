package org.springframework.boot.logging.structured;

import java.nio.charset.Charset;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.util.LambdaSafe;

public abstract class JsonWriterStructuredLogFormatter<E> implements StructuredLogFormatter<E> {
   private final JsonWriter<E> jsonWriter;

   protected JsonWriterStructuredLogFormatter(Consumer<JsonWriter.Members<E>> members, @Nullable StructuredLoggingJsonMembersCustomizer<?> customizer) {
      this(JsonWriter.of(customized(members, customizer)).withNewLineAtEnd());
   }

   private static <E> Consumer<JsonWriter.Members<E>> customized(
      Consumer<JsonWriter.Members<E>> members, @Nullable StructuredLoggingJsonMembersCustomizer<?> customizer
   ) {
      return customizer != null ? members.andThen(customizeWith(customizer)) : members;
   }

   private static <E> Consumer<JsonWriter.Members<E>> customizeWith(StructuredLoggingJsonMembersCustomizer<?> customizer) {
      return members -> LambdaSafe.callback(StructuredLoggingJsonMembersCustomizer.class, customizer, members).invoke(instance -> instance.customize(members));
   }

   protected JsonWriterStructuredLogFormatter(JsonWriter<E> jsonWriter) {
      this.jsonWriter = jsonWriter;
   }

   @Override
   public String format(E event) {
      return this.jsonWriter.writeToString(event);
   }

   @Override
   public byte[] formatAsBytes(E event, Charset charset) {
      return this.jsonWriter.write(event).toByteArray(charset);
   }
}
