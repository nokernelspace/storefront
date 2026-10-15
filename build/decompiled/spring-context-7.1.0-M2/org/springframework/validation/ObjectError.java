package org.springframework.validation;

import org.jspecify.annotations.Nullable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.util.Assert;

public class ObjectError extends DefaultMessageSourceResolvable {
   private final String objectName;
   private transient @Nullable Object source;

   public ObjectError(String objectName, @Nullable String defaultMessage) {
      this(objectName, null, null, defaultMessage);
   }

   public ObjectError(String objectName, String @Nullable [] codes, Object @Nullable [] arguments, @Nullable String defaultMessage) {
      super(codes, arguments, defaultMessage);
      Assert.notNull(objectName, "Object name must not be null");
      this.objectName = objectName;
   }

   public String getObjectName() {
      return this.objectName;
   }

   public void wrap(Object source) {
      if (this.source != null) {
         throw new IllegalStateException("Already wrapping " + this.source);
      }

      this.source = source;
   }

   public <T> T unwrap(Class<T> sourceType) {
      if (sourceType.isInstance(this.source)) {
         return sourceType.cast(this.source);
      }

      if (this.source instanceof Throwable throwable) {
         Throwable cause = throwable.getCause();
         if (sourceType.isInstance(cause)) {
            return sourceType.cast(cause);
         }
      }

      throw new IllegalArgumentException("No source object of the given type available: " + sourceType);
   }

   public boolean contains(Class<?> sourceType) {
      return sourceType.isInstance(this.source) || this.source instanceof Throwable throwable && sourceType.isInstance(throwable.getCause());
   }

   @Override
   public boolean equals(@Nullable Object other) {
      if (this == other) {
         return true;
      } else if (other != null && other.getClass() == this.getClass() && super.equals(other)) {
         ObjectError otherError = (ObjectError)other;
         return this.getObjectName().equals(otherError.getObjectName());
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return 29 * super.hashCode() + this.getObjectName().hashCode();
   }

   @Override
   public String toString() {
      return "Error in object '" + this.objectName + "': " + this.resolvableToString();
   }
}
