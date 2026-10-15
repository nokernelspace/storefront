package org.springframework.boot.origin;

import org.jspecify.annotations.Nullable;
import org.springframework.core.env.PropertySource;
import org.springframework.util.Assert;

public class PropertySourceOrigin implements Origin, OriginProvider {
   private final PropertySource<?> propertySource;
   private final String propertyName;
   private final @Nullable Origin origin;

   public PropertySourceOrigin(PropertySource<?> propertySource, String propertyName) {
      this(propertySource, propertyName, null);
   }

   public PropertySourceOrigin(PropertySource<?> propertySource, String propertyName, @Nullable Origin origin) {
      Assert.notNull(propertySource, "'propertySource' must not be null");
      Assert.hasLength(propertyName, "'propertyName' must not be empty");
      this.propertySource = propertySource;
      this.propertyName = propertyName;
      this.origin = origin;
   }

   public PropertySource<?> getPropertySource() {
      return this.propertySource;
   }

   public String getPropertyName() {
      return this.propertyName;
   }

   @Override
   public @Nullable Origin getOrigin() {
      return this.origin;
   }

   @Override
   public @Nullable Origin getParent() {
      return this.origin != null ? this.origin.getParent() : null;
   }

   @Override
   public String toString() {
      return this.origin != null ? this.origin.toString() : "\"" + this.propertyName + "\" from property source \"" + this.propertySource.getName() + "\"";
   }

   public static Origin get(PropertySource<?> propertySource, String name) {
      Origin origin = OriginLookup.getOrigin(propertySource, name);
      return origin instanceof PropertySourceOrigin ? origin : new PropertySourceOrigin(propertySource, name, origin);
   }
}
