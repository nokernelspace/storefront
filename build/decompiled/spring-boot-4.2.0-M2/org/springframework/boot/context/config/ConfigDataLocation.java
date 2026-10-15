package org.springframework.boot.context.config;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginProvider;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public final class ConfigDataLocation implements OriginProvider {
   private static final ConfigDataLocation EMPTY = new ConfigDataLocation(false, "", null);
   public static final String OPTIONAL_PREFIX = "optional:";
   private final boolean optional;
   private final String value;
   private final @Nullable Origin origin;

   private ConfigDataLocation(boolean optional, String value, @Nullable Origin origin) {
      this.value = value;
      this.optional = optional;
      this.origin = origin;
   }

   public boolean isOptional() {
      return this.optional;
   }

   public String getValue() {
      return this.value;
   }

   public boolean hasPrefix(String prefix) {
      return this.value.startsWith(prefix);
   }

   public String getNonPrefixedValue(String prefix) {
      return !this.hasPrefix(prefix) ? this.value : this.value.substring(prefix.length());
   }

   @Override
   public @Nullable Origin getOrigin() {
      return this.origin;
   }

   public ConfigDataLocation[] split() {
      return this.split(";");
   }

   public ConfigDataLocation[] split(String delimiter) {
      Assert.state(!this.value.isEmpty(), "Unable to split empty locations");
      String[] values = StringUtils.delimitedListToStringArray(this.toString(), delimiter);
      ConfigDataLocation[] result = new ConfigDataLocation[values.length];

      for (int i = 0; i < values.length; i++) {
         int index = i;
         ConfigDataLocation configDataLocation = of(values[index]);
         result[i] = configDataLocation.withOrigin(this.getOrigin());
      }

      return result;
   }

   ConfigDataLocation withOrigin(@Nullable Origin origin) {
      return new ConfigDataLocation(this.optional, this.value, origin);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (obj != null && this.getClass() == obj.getClass()) {
         ConfigDataLocation other = (ConfigDataLocation)obj;
         return this.value.equals(other.value);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return this.value.hashCode();
   }

   @Override
   public String toString() {
      return !this.optional ? this.value : "optional:" + this.value;
   }

   public static ConfigDataLocation of(@Nullable String location) {
      boolean optional = location != null && location.startsWith("optional:");
      String value = location != null && optional ? location.substring("optional:".length()) : location;
      return StringUtils.hasText(value) ? new ConfigDataLocation(optional, value, null) : EMPTY;
   }

   static boolean isNotEmpty(@Nullable ConfigDataLocation location) {
      return location != null && !location.getValue().isEmpty();
   }
}
