package org.springframework.aot.hint;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

@Deprecated(since = "7.0.6", forRemoval = true)
public class SerializationHints {
   private final Set<JavaSerializationHint> javaSerializationHints = new LinkedHashSet<>();

   public Stream<JavaSerializationHint> javaSerializationHints() {
      return this.javaSerializationHints.stream();
   }

   public SerializationHints registerType(TypeReference type, @Nullable Consumer<JavaSerializationHint.Builder> serializationHint) {
      JavaSerializationHint.Builder builder = new JavaSerializationHint.Builder(type);
      if (serializationHint != null) {
         serializationHint.accept(builder);
      }

      this.javaSerializationHints.add(builder.build());
      return this;
   }

   public SerializationHints registerType(TypeReference type) {
      return this.registerType(type, null);
   }

   public SerializationHints registerType(Class<? extends Serializable> type, @Nullable Consumer<JavaSerializationHint.Builder> serializationHint) {
      return this.registerType(TypeReference.of(type), serializationHint);
   }

   public SerializationHints registerType(Class<? extends Serializable> type) {
      return this.registerType(type, null);
   }
}
