package org.springframework.boot.logging.structured;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.context.properties.bind.BindableRuntimeHintsRegistrar;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.json.JsonWriter;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

public record GraylogExtendedLogFormatProperties(@Nullable String host, GraylogExtendedLogFormatProperties.Service service) {
   static final GraylogExtendedLogFormatProperties NONE = new GraylogExtendedLogFormatProperties(null, null);

   public GraylogExtendedLogFormatProperties(@Nullable String host, GraylogExtendedLogFormatProperties.@Nullable Service service) {
      this.host = host;
      this.service = service != null ? service : GraylogExtendedLogFormatProperties.Service.NONE;
   }

   GraylogExtendedLogFormatProperties withDefaults(Environment environment) {
      String name = withFallbackProperty(environment, this.host, "spring.application.name");
      GraylogExtendedLogFormatProperties.Service service = this.service.withDefaults(environment);
      return new GraylogExtendedLogFormatProperties(name, service);
   }

   static @Nullable String withFallbackProperty(Environment environment, @Nullable String value, String property) {
      return !StringUtils.hasLength(value) ? environment.getProperty(property) : value;
   }

   public void jsonMembers(JsonWriter.Members<?> members) {
      members.add("host", this::host).whenHasLength();
      this.service.jsonMembers(members);
   }

   public static GraylogExtendedLogFormatProperties get(Environment environment) {
      return Binder.get(environment).bind("logging.structured.gelf", GraylogExtendedLogFormatProperties.class).orElse(NONE).withDefaults(environment);
   }

   static class GraylogExtendedLogFormatPropertiesRuntimeHints implements RuntimeHintsRegistrar {
      public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
         BindableRuntimeHintsRegistrar.forTypes(GraylogExtendedLogFormatProperties.class).registerHints(hints, classLoader);
      }
   }

   public record Service(@Nullable String version) {
      static final GraylogExtendedLogFormatProperties.Service NONE = new GraylogExtendedLogFormatProperties.Service(null);

      GraylogExtendedLogFormatProperties.Service withDefaults(Environment environment) {
         String version = GraylogExtendedLogFormatProperties.withFallbackProperty(environment, this.version, "spring.application.version");
         return new GraylogExtendedLogFormatProperties.Service(version);
      }

      void jsonMembers(JsonWriter.Members<?> members) {
         members.add("_service_version", this::version).whenHasLength();
      }
   }
}
