package org.springframework.boot.context.properties.source;

import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public class InvalidConfigurationPropertyValueException extends RuntimeException {
   private final String name;
   private final @Nullable Object value;
   private final @Nullable String reason;

   public InvalidConfigurationPropertyValueException(String name, @Nullable Object value, @Nullable String reason) {
      this(name, value, reason, null);
   }

   public InvalidConfigurationPropertyValueException(String name, @Nullable Object value, @Nullable String reason, @Nullable Throwable cause) {
      super("Property " + name + " with value '" + value + "' is invalid: " + reason, cause);
      Assert.notNull(name, "'name' must not be null");
      this.name = name;
      this.value = value;
      this.reason = reason;
   }

   public String getName() {
      return this.name;
   }

   public @Nullable Object getValue() {
      return this.value;
   }

   public @Nullable String getReason() {
      return this.reason;
   }
}
