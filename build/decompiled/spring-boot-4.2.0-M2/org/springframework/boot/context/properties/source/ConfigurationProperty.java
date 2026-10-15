package org.springframework.boot.context.properties.source;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginProvider;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.core.style.ToStringCreator;
import org.springframework.lang.Contract;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

public final class ConfigurationProperty implements OriginProvider, Comparable<ConfigurationProperty> {
   private final ConfigurationPropertyName name;
   private final Object value;
   private final @Nullable ConfigurationPropertySource source;
   private final @Nullable Origin origin;

   public ConfigurationProperty(ConfigurationPropertyName name, Object value, @Nullable Origin origin) {
      this(null, name, value, origin);
   }

   private ConfigurationProperty(@Nullable ConfigurationPropertySource source, ConfigurationPropertyName name, Object value, @Nullable Origin origin) {
      Assert.notNull(name, "'name' must not be null");
      Assert.notNull(value, "'value' must not be null");
      this.source = source;
      this.name = name;
      this.value = value;
      this.origin = origin;
   }

   public @Nullable ConfigurationPropertySource getSource() {
      return this.source;
   }

   public ConfigurationPropertyName getName() {
      return this.name;
   }

   public Object getValue() {
      return this.value;
   }

   @Override
   public @Nullable Origin getOrigin() {
      return this.origin;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (obj != null && this.getClass() == obj.getClass()) {
         ConfigurationProperty other = (ConfigurationProperty)obj;
         boolean result = true;
         result = result && ObjectUtils.nullSafeEquals(this.name, other.name);
         return result && ObjectUtils.nullSafeEquals(this.value, other.value);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      int result = ObjectUtils.nullSafeHashCode(this.name);
      return 31 * result + ObjectUtils.nullSafeHashCode(this.value);
   }

   @Override
   public String toString() {
      return new ToStringCreator(this).append("name", this.name).append("value", this.value).append("origin", this.origin).toString();
   }

   public int compareTo(ConfigurationProperty other) {
      return this.name.compareTo(other.name);
   }

   @Contract("_, !null -> !null")
   static @Nullable ConfigurationProperty of(ConfigurationPropertyName name, @Nullable OriginTrackedValue value) {
      return value == null ? null : new ConfigurationProperty(name, value.getValue(), value.getOrigin());
   }

   @Contract("_, _, !null, _ -> !null")
   static @Nullable ConfigurationProperty of(
      @Nullable ConfigurationPropertySource source, ConfigurationPropertyName name, @Nullable Object value, @Nullable Origin origin
   ) {
      return value == null ? null : new ConfigurationProperty(source, name, value, origin);
   }
}
