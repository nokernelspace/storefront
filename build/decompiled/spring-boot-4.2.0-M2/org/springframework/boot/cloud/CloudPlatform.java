package org.springframework.boot.cloud;

import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

public enum CloudPlatform {
   NONE {
      @Override
      public boolean isDetected(Environment environment) {
         return false;
      }

      @Override
      public boolean isUsingForwardHeaders() {
         return false;
      }
   },
   CLOUD_FOUNDRY {
      @Override
      public boolean isDetected(Environment environment) {
         return environment.containsProperty("VCAP_APPLICATION") || environment.containsProperty("VCAP_SERVICES");
      }
   },
   HEROKU {
      @Override
      public boolean isDetected(Environment environment) {
         return environment.containsProperty("DYNO");
      }
   },
   SAP {
      @Override
      public boolean isDetected(Environment environment) {
         return environment.containsProperty("HC_LANDSCAPE");
      }
   },
   NOMAD {
      @Override
      public boolean isDetected(Environment environment) {
         return environment.containsProperty("NOMAD_ALLOC_ID");
      }
   },
   KUBERNETES {
      private static final String KUBERNETES_SERVICE_HOST = "KUBERNETES_SERVICE_HOST";
      private static final String KUBERNETES_SERVICE_PORT = "KUBERNETES_SERVICE_PORT";
      private static final String SERVICE_HOST_SUFFIX = "_SERVICE_HOST";
      private static final String SERVICE_PORT_SUFFIX = "_SERVICE_PORT";

      @Override
      public boolean isDetected(Environment environment) {
         return environment instanceof ConfigurableEnvironment configurableEnvironment ? this.isAutoDetected(configurableEnvironment) : false;
      }

      private boolean isAutoDetected(ConfigurableEnvironment environment) {
         PropertySource<?> environmentPropertySource = environment.getPropertySources().get("systemEnvironment");
         if (environmentPropertySource != null) {
            if (environmentPropertySource.containsProperty("KUBERNETES_SERVICE_HOST") && environmentPropertySource.containsProperty("KUBERNETES_SERVICE_PORT")) {
               return true;
            }

            if (environmentPropertySource instanceof EnumerablePropertySource<?> enumerablePropertySource) {
               return this.isAutoDetected(enumerablePropertySource);
            }
         }

         return false;
      }

      private boolean isAutoDetected(EnumerablePropertySource<?> environmentPropertySource) {
         for (String propertyName : environmentPropertySource.getPropertyNames()) {
            if (propertyName.endsWith("_SERVICE_HOST")) {
               String serviceName = propertyName.substring(0, propertyName.length() - "_SERVICE_HOST".length());
               if (environmentPropertySource.getProperty(serviceName + "_SERVICE_PORT") != null) {
                  return true;
               }
            }
         }

         return false;
      }
   },
   AZURE_APP_SERVICE {
      private final List<String> azureEnvVariables = Arrays.asList("WEBSITE_SITE_NAME", "WEBSITE_INSTANCE_ID", "WEBSITE_RESOURCE_GROUP", "WEBSITE_SKU");

      @Override
      public boolean isDetected(Environment environment) {
         return this.azureEnvVariables.stream().allMatch(environment::containsProperty);
      }
   },
   AWS_ECS {
      @Override
      public boolean isDetected(Environment environment) {
         String awsExecutionEnv = environment.getProperty("AWS_EXECUTION_ENV");
         return awsExecutionEnv != null && awsExecutionEnv.startsWith("AWS_ECS");
      }
   };

   private static final String PROPERTY_NAME = "spring.main.cloud-platform";

   public boolean isActive(Environment environment) {
      String platformProperty = environment.getProperty("spring.main.cloud-platform");
      return this.isEnforced(platformProperty) || platformProperty == null && this.isDetected(environment);
   }

   public boolean isEnforced(Environment environment) {
      return this.isEnforced(environment.getProperty("spring.main.cloud-platform"));
   }

   public boolean isEnforced(Binder binder) {
      return this.isEnforced(binder.bind("spring.main.cloud-platform", String.class).orElse(null));
   }

   private boolean isEnforced(@Nullable String platform) {
      return this.name().equalsIgnoreCase(platform);
   }

   public abstract boolean isDetected(Environment environment);

   public boolean isUsingForwardHeaders() {
      return true;
   }

   public static @Nullable CloudPlatform getActive(@Nullable Environment environment) {
      if (environment != null) {
         for (CloudPlatform cloudPlatform : values()) {
            if (cloudPlatform.isActive(environment)) {
               return cloudPlatform;
            }
         }
      }

      return null;
   }
}
