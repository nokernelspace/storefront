package org.springframework.core.convert.converter;

import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.util.Assert;

public interface GenericConverter {
   @Nullable Set<GenericConverter.ConvertiblePair> getConvertibleTypes();

   @Nullable Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType);

   final class ConvertiblePair {
      private final Class<?> sourceType;
      private final Class<?> targetType;

      public ConvertiblePair(Class<?> sourceType, Class<?> targetType) {
         Assert.notNull(sourceType, "Source type must not be null");
         Assert.notNull(targetType, "Target type must not be null");
         this.sourceType = sourceType;
         this.targetType = targetType;
      }

      public Class<?> getSourceType() {
         return this.sourceType;
      }

      public Class<?> getTargetType() {
         return this.targetType;
      }

      @Override
      public boolean equals(@Nullable Object other) {
         if (this == other) {
            return true;
         } else if (other != null && other.getClass() == GenericConverter.ConvertiblePair.class) {
            GenericConverter.ConvertiblePair otherPair = (GenericConverter.ConvertiblePair)other;
            return this.sourceType == otherPair.sourceType && this.targetType == otherPair.targetType;
         } else {
            return false;
         }
      }

      @Override
      public int hashCode() {
         return this.sourceType.hashCode() * 31 + this.targetType.hashCode();
      }

      @Override
      public String toString() {
         return this.sourceType.getName() + " -> " + this.targetType.getName();
      }
   }
}
