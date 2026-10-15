package org.springframework.aot.hint;

import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

public interface TypeReference extends Comparable<TypeReference> {
   String getName();

   String getCanonicalName();

   String getPackageName();

   String getSimpleName();

   @Nullable TypeReference getEnclosingType();

   static TypeReference of(Class<?> type) {
      return ReflectionTypeReference.of(type);
   }

   static TypeReference of(String className) {
      return SimpleTypeReference.of(className);
   }

   static List<TypeReference> listOf(Class<?>... types) {
      return Arrays.stream(types).map(TypeReference::of).toList();
   }
}
