package org.springframework.boot.logging.structured;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.context.properties.bind.BindableRuntimeHintsRegistrar;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.json.JsonWriter;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

public record ElasticCommonSchemaProperties(ElasticCommonSchemaProperties.Service service) {
   static final ElasticCommonSchemaProperties NONE = new ElasticCommonSchemaProperties(ElasticCommonSchemaProperties.Service.NONE);

   ElasticCommonSchemaProperties withDefaults(Environment environment) {
      ElasticCommonSchemaProperties.Service service = this.service.withDefaults(environment);
      return new ElasticCommonSchemaProperties(service);
   }

   static @Nullable String withFallbackProperty(Environment environment, @Nullable String value, String property) {
      return !StringUtils.hasLength(value) ? environment.getProperty(property) : value;
   }

   public void jsonMembers(JsonWriter.Members<?> members) {
      this.service.jsonMembers(members);
   }

   public static ElasticCommonSchemaProperties get(Environment environment) {
      return Binder.get(environment).bind("logging.structured.ecs", ElasticCommonSchemaProperties.class).orElse(NONE).withDefaults(environment);
   }

   static class ElasticCommonSchemaPropertiesRuntimeHints implements RuntimeHintsRegistrar {
      public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
         BindableRuntimeHintsRegistrar.forTypes(ElasticCommonSchemaProperties.class).registerHints(hints, classLoader);
      }
   }

   public record Service(@Nullable String name, @Nullable String version, @Nullable String environment, @Nullable String nodeName) {
      static final ElasticCommonSchemaProperties.Service NONE = new ElasticCommonSchemaProperties.Service(null, null, null, null);

      void jsonMembers(JsonWriter.Members<?> members) {
         members.add("service").usingMembers(service -> {
            service.add("name", this::name).whenHasLength();
            service.add("version", this::version).whenHasLength();
            service.add("environment", this::environment).whenHasLength();
            service.add("node").usingMembers(node -> node.add("name", this::nodeName).whenHasLength());
         });
      }

      ElasticCommonSchemaProperties.Service withDefaults(Environment environment) {
         String name = ElasticCommonSchemaProperties.withFallbackProperty(environment, this.name, "spring.application.name");
         String version = ElasticCommonSchemaProperties.withFallbackProperty(environment, this.version, "spring.application.version");
         return new ElasticCommonSchemaProperties.Service(name, version, this.environment, this.nodeName);
      }
   }
}
