package org.springframework.boot.context.config;

import java.util.EventListener;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.PropertySource;

public interface ConfigDataEnvironmentUpdateListener extends EventListener {
   ConfigDataEnvironmentUpdateListener NONE = new ConfigDataEnvironmentUpdateListener() {};

   default void onPropertySourceAdded(PropertySource<?> propertySource, @Nullable ConfigDataLocation location, @Nullable ConfigDataResource resource) {
   }

   default void onSetProfiles(Profiles profiles) {
   }

   default ConfigData.Options onConfigDataOptions(ConfigData configData, PropertySource<?> propertySource, ConfigData.Options options) {
      return options;
   }
}
