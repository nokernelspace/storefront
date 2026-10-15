package org.springframework.boot.info;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.ImportRuntimeHints;

@ImportRuntimeHints(BuildProperties.BuildPropertiesRuntimeHints.class)
public class BuildProperties extends InfoProperties {
   public BuildProperties(Properties entries) {
      super(processEntries(entries));
   }

   public @Nullable String getGroup() {
      return this.get("group");
   }

   public @Nullable String getArtifact() {
      return this.get("artifact");
   }

   public @Nullable String getName() {
      return this.get("name");
   }

   public @Nullable String getVersion() {
      return this.get("version");
   }

   public @Nullable Instant getTime() {
      return this.getInstant("time");
   }

   private static Properties processEntries(Properties properties) {
      coerceDate(properties, "time");
      return properties;
   }

   private static void coerceDate(Properties properties, String key) {
      String value = properties.getProperty(key);
      if (value != null) {
         try {
            String updatedValue = String.valueOf(DateTimeFormatter.ISO_INSTANT.parse(value, Instant::from).toEpochMilli());
            properties.setProperty(key, updatedValue);
         } catch (DateTimeException var4) {
         }
      }
   }

   static class BuildPropertiesRuntimeHints implements RuntimeHintsRegistrar {
      public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
         hints.resources().registerPattern("META-INF/build-info.properties");
      }
   }
}
