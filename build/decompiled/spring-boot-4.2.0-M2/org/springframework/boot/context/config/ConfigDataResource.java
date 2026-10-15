package org.springframework.boot.context.config;

public abstract class ConfigDataResource {
   private final boolean optional;

   public ConfigDataResource() {
      this(false);
   }

   protected ConfigDataResource(boolean optional) {
      this.optional = optional;
   }

   boolean isOptional() {
      return this.optional;
   }
}
