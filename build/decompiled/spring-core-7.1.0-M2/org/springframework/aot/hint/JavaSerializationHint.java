package org.springframework.aot.hint;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

@Deprecated(since = "7.0.6", forRemoval = true)
public final class JavaSerializationHint implements ConditionalHint {
   private final TypeReference type;
   private final @Nullable TypeReference reachableType;

   JavaSerializationHint(JavaSerializationHint.Builder builder) {
      this.type = builder.type;
      this.reachableType = builder.reachableType;
   }

   public TypeReference getType() {
      return this.type;
   }

   @Override
   public @Nullable TypeReference getReachableType() {
      return this.reachableType;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof JavaSerializationHint that && this.type.equals(that.type) && Objects.equals(this.reachableType, that.reachableType);
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.type, this.reachableType);
   }

   public static class Builder {
      private final TypeReference type;
      private @Nullable TypeReference reachableType;

      Builder(TypeReference type) {
         this.type = type;
      }

      public JavaSerializationHint.Builder onReachableType(TypeReference reachableType) {
         this.reachableType = reachableType;
         return this;
      }

      JavaSerializationHint build() {
         return new JavaSerializationHint(this);
      }
   }
}
