package org.springframework.aot.hint;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class ResourceBundleHint implements ConditionalHint {
   private final String baseName;
   private final @Nullable TypeReference reachableType;

   ResourceBundleHint(ResourceBundleHint.Builder builder) {
      this.baseName = builder.baseName;
      this.reachableType = builder.reachableType;
   }

   public String getBaseName() {
      return this.baseName;
   }

   @Override
   public @Nullable TypeReference getReachableType() {
      return this.reachableType;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof ResourceBundleHint that && this.baseName.equals(that.baseName) && Objects.equals(this.reachableType, that.reachableType);
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.baseName, this.reachableType);
   }

   public static class Builder {
      private String baseName;
      private @Nullable TypeReference reachableType;

      Builder(String baseName) {
         this.baseName = baseName;
      }

      public ResourceBundleHint.Builder onReachableType(TypeReference reachableType) {
         this.reachableType = reachableType;
         return this;
      }

      public ResourceBundleHint.Builder baseName(String baseName) {
         this.baseName = baseName;
         return this;
      }

      ResourceBundleHint build() {
         return new ResourceBundleHint(this);
      }
   }
}
