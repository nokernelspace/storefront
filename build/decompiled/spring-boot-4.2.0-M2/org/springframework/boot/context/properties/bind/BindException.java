package org.springframework.boot.context.properties.bind;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.source.ConfigurationProperty;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginProvider;

public class BindException extends RuntimeException implements OriginProvider {
   private final Bindable<?> target;
   private final @Nullable ConfigurationProperty property;
   private final ConfigurationPropertyName name;

   BindException(ConfigurationPropertyName name, Bindable<?> target, @Nullable ConfigurationProperty property, @Nullable Throwable cause) {
      super(buildMessage(name, target), cause);
      this.name = name;
      this.target = target;
      this.property = property;
   }

   public ConfigurationPropertyName getName() {
      return this.name;
   }

   public Bindable<?> getTarget() {
      return this.target;
   }

   public @Nullable ConfigurationProperty getProperty() {
      return this.property;
   }

   @Override
   public @Nullable Origin getOrigin() {
      return Origin.from(this.name);
   }

   private static String buildMessage(@Nullable ConfigurationPropertyName name, Bindable<?> target) {
      StringBuilder message = new StringBuilder();
      message.append("Failed to bind properties");
      message.append(name != null ? " under '" + name + "'" : "");
      message.append(" to ").append(target.getType());
      return message.toString();
   }
}
