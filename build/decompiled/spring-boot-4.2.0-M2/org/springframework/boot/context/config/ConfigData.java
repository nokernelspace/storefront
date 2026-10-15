package org.springframework.boot.context.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.PropertySource;
import org.springframework.util.Assert;

public final class ConfigData {
   private final List<PropertySource<?>> propertySources;
   private final ConfigData.PropertySourceOptions propertySourceOptions;
   public static final ConfigData EMPTY = new ConfigData(Collections.emptySet());

   public ConfigData(Collection<? extends PropertySource<?>> propertySources, ConfigData.Option... options) {
      this(propertySources, ConfigData.PropertySourceOptions.always(ConfigData.Options.of(options)));
   }

   public ConfigData(Collection<? extends PropertySource<?>> propertySources, ConfigData.PropertySourceOptions propertySourceOptions) {
      Assert.notNull(propertySources, "'propertySources' must not be null");
      Assert.notNull(propertySourceOptions, "'propertySourceOptions' must not be null");
      this.propertySources = Collections.unmodifiableList(new ArrayList<>(propertySources));
      this.propertySourceOptions = propertySourceOptions;
   }

   public List<PropertySource<?>> getPropertySources() {
      return this.propertySources;
   }

   public ConfigData.Options getOptions(PropertySource<?> propertySource) {
      ConfigData.Options options = this.propertySourceOptions.get(propertySource);
      return options != null ? options : ConfigData.Options.NONE;
   }

   private static class AlwaysPropertySourceOptions implements ConfigData.PropertySourceOptions {
      private final ConfigData.Options options;

      AlwaysPropertySourceOptions(ConfigData.Options options) {
         this.options = options;
      }

      @Override
      public ConfigData.Options get(PropertySource<?> propertySource) {
         return this.options;
      }
   }

   public enum Option {
      IGNORE_IMPORTS,
      IGNORE_PROFILES,
      PROFILE_SPECIFIC;
   }

   public static final class Options {
      public static final ConfigData.Options NONE = new ConfigData.Options(Collections.emptySet());
      private final Set<ConfigData.Option> options;

      private Options(Set<ConfigData.Option> options) {
         this.options = Collections.unmodifiableSet(options);
      }

      Set<ConfigData.Option> asSet() {
         return this.options;
      }

      public boolean contains(ConfigData.Option option) {
         return this.options.contains(option);
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         } else if (obj != null && this.getClass() == obj.getClass()) {
            ConfigData.Options other = (ConfigData.Options)obj;
            return this.options.equals(other.options);
         } else {
            return false;
         }
      }

      @Override
      public int hashCode() {
         return this.options.hashCode();
      }

      @Override
      public String toString() {
         return this.options.toString();
      }

      public ConfigData.Options without(ConfigData.Option option) {
         return this.copy(options -> options.remove(option));
      }

      public ConfigData.Options with(ConfigData.Option option) {
         return this.copy(options -> options.add(option));
      }

      private ConfigData.Options copy(Consumer<EnumSet<ConfigData.Option>> processor) {
         EnumSet<ConfigData.Option> options = !this.options.isEmpty() ? EnumSet.copyOf(this.options) : EnumSet.noneOf(ConfigData.Option.class);
         processor.accept(options);
         return new ConfigData.Options(options);
      }

      public static ConfigData.Options of(ConfigData.Option... options) {
         Assert.notNull(options, "'options' must not be null");
         return options.length == 0 ? NONE : new ConfigData.Options(EnumSet.copyOf(Arrays.asList(options)));
      }
   }

   @FunctionalInterface
   public interface PropertySourceOptions {
      ConfigData.PropertySourceOptions ALWAYS_NONE = new ConfigData.AlwaysPropertySourceOptions(ConfigData.Options.NONE);

      ConfigData.@Nullable Options get(PropertySource<?> propertySource);

      static ConfigData.PropertySourceOptions always(ConfigData.Option... options) {
         return always(ConfigData.Options.of(options));
      }

      static ConfigData.PropertySourceOptions always(ConfigData.Options options) {
         return options == ConfigData.Options.NONE ? ALWAYS_NONE : new ConfigData.AlwaysPropertySourceOptions(options);
      }
   }
}
