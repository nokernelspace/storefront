package org.springframework.boot.context.config;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Supplier;
import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.ConfigurableBootstrapContext;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

public class ConfigDataEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
   public static final int ORDER = -2147483638;
   public static final String ON_LOCATION_NOT_FOUND_PROPERTY = "spring.config.on-not-found";
   private final DeferredLogFactory logFactory;
   private final Log logger;
   private final ConfigurableBootstrapContext bootstrapContext;
   private final @Nullable ConfigDataEnvironmentUpdateListener environmentUpdateListener;

   public ConfigDataEnvironmentPostProcessor(DeferredLogFactory logFactory, ConfigurableBootstrapContext bootstrapContext) {
      this(logFactory, bootstrapContext, null);
   }

   private ConfigDataEnvironmentPostProcessor(
      DeferredLogFactory logFactory, ConfigurableBootstrapContext bootstrapContext, @Nullable ConfigDataEnvironmentUpdateListener environmentUpdateListener
   ) {
      this.logFactory = logFactory;
      this.logger = logFactory.getLog(this.getClass());
      this.bootstrapContext = bootstrapContext;
      this.environmentUpdateListener = environmentUpdateListener;
   }

   public int getOrder() {
      return -2147483638;
   }

   @Override
   public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
      this.postProcessEnvironment(environment, application.getResourceLoader(), application.getAdditionalProfiles());
   }

   void postProcessEnvironment(ConfigurableEnvironment environment, @Nullable ResourceLoader resourceLoader, Collection<String> additionalProfiles) {
      this.logger.trace("Post-processing environment to add config data");
      resourceLoader = (ResourceLoader)(resourceLoader != null ? resourceLoader : new DefaultResourceLoader());
      this.getConfigDataEnvironment(environment, resourceLoader, additionalProfiles).processAndApply();
   }

   ConfigDataEnvironment getConfigDataEnvironment(ConfigurableEnvironment environment, ResourceLoader resourceLoader, Collection<String> additionalProfiles) {
      return new ConfigDataEnvironment(this.logFactory, this.bootstrapContext, environment, resourceLoader, additionalProfiles, this.environmentUpdateListener);
   }

   public static void applyTo(ConfigurableEnvironment environment) {
      applyTo(environment, null, null, Collections.emptyList());
   }

   public static void applyTo(
      ConfigurableEnvironment environment, ResourceLoader resourceLoader, @Nullable ConfigurableBootstrapContext bootstrapContext, String... additionalProfiles
   ) {
      applyTo(environment, resourceLoader, bootstrapContext, Arrays.asList(additionalProfiles));
   }

   public static void applyTo(
      ConfigurableEnvironment environment,
      @Nullable ResourceLoader resourceLoader,
      @Nullable ConfigurableBootstrapContext bootstrapContext,
      Collection<String> additionalProfiles
   ) {
      DeferredLogFactory logFactory = Supplier::get;
      bootstrapContext = bootstrapContext != null ? bootstrapContext : new DefaultBootstrapContext();
      ConfigDataEnvironmentPostProcessor postProcessor = new ConfigDataEnvironmentPostProcessor(logFactory, bootstrapContext);
      postProcessor.postProcessEnvironment(environment, resourceLoader, additionalProfiles);
   }

   public static void applyTo(
      ConfigurableEnvironment environment,
      @Nullable ResourceLoader resourceLoader,
      @Nullable ConfigurableBootstrapContext bootstrapContext,
      Collection<String> additionalProfiles,
      ConfigDataEnvironmentUpdateListener environmentUpdateListener
   ) {
      DeferredLogFactory logFactory = Supplier::get;
      bootstrapContext = bootstrapContext != null ? bootstrapContext : new DefaultBootstrapContext();
      ConfigDataEnvironmentPostProcessor postProcessor = new ConfigDataEnvironmentPostProcessor(logFactory, bootstrapContext, environmentUpdateListener);
      postProcessor.postProcessEnvironment(environment, resourceLoader, additionalProfiles);
   }
}
