package org.springframework.aot.hint.predicate;

import java.util.function.Predicate;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.util.Assert;

@Deprecated(since = "7.0.6", forRemoval = true)
public class SerializationHintsPredicates {
   SerializationHintsPredicates() {
   }

   public Predicate<RuntimeHints> onType(Class<?> type) {
      Assert.notNull(type, "'type' must not be null");
      return this.onType(TypeReference.of(type));
   }

   public Predicate<RuntimeHints> onType(TypeReference typeReference) {
      Assert.notNull(typeReference, "'typeReference' must not be null");
      return hints -> hints.serialization().javaSerializationHints().anyMatch(hint -> hint.getType().equals(typeReference));
   }
}
