package org.springframework.boot;

import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.context.properties.bind.BindableRuntimeHintsRegistrar;
import org.springframework.boot.logging.LoggingSystemProperty;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

class ApplicationProperties {
   private boolean allowBeanDefinitionOverriding;
   private boolean allowCircularReferences;
   private Banner.@Nullable Mode bannerMode;
   private boolean keepAlive;
   private boolean lazyInitialization;
   private boolean logStartupInfo = true;
   private boolean registerShutdownHook = true;
   private Set<String> sources = new LinkedHashSet<>();
   private @Nullable WebApplicationType webApplicationType;

   boolean isAllowBeanDefinitionOverriding() {
      return this.allowBeanDefinitionOverriding;
   }

   void setAllowBeanDefinitionOverriding(boolean allowBeanDefinitionOverriding) {
      this.allowBeanDefinitionOverriding = allowBeanDefinitionOverriding;
   }

   boolean isAllowCircularReferences() {
      return this.allowCircularReferences;
   }

   void setAllowCircularReferences(boolean allowCircularReferences) {
      this.allowCircularReferences = allowCircularReferences;
   }

   Banner.Mode getBannerMode(Environment environment) {
      if (this.bannerMode != null) {
         return this.bannerMode;
      }

      String applicationPropertyName = LoggingSystemProperty.CONSOLE_STRUCTURED_FORMAT.getApplicationPropertyName();
      Assert.state(applicationPropertyName != null, "applicationPropertyName must not be null");
      boolean structuredLoggingEnabled = environment.containsProperty(applicationPropertyName);
      return structuredLoggingEnabled ? Banner.Mode.OFF : Banner.Mode.CONSOLE;
   }

   void setBannerMode(Banner.@Nullable Mode bannerMode) {
      this.bannerMode = bannerMode;
   }

   boolean isKeepAlive() {
      return this.keepAlive;
   }

   void setKeepAlive(boolean keepAlive) {
      this.keepAlive = keepAlive;
   }

   boolean isLazyInitialization() {
      return this.lazyInitialization;
   }

   void setLazyInitialization(boolean lazyInitialization) {
      this.lazyInitialization = lazyInitialization;
   }

   boolean isLogStartupInfo() {
      return this.logStartupInfo;
   }

   void setLogStartupInfo(boolean logStartupInfo) {
      this.logStartupInfo = logStartupInfo;
   }

   boolean isRegisterShutdownHook() {
      return this.registerShutdownHook;
   }

   void setRegisterShutdownHook(boolean registerShutdownHook) {
      this.registerShutdownHook = registerShutdownHook;
   }

   Set<String> getSources() {
      return this.sources;
   }

   void setSources(Set<String> sources) {
      this.sources = new LinkedHashSet<>(sources);
   }

   @Nullable WebApplicationType getWebApplicationType() {
      return this.webApplicationType;
   }

   void setWebApplicationType(@Nullable WebApplicationType webApplicationType) {
      this.webApplicationType = webApplicationType;
   }

   static class ApplicationPropertiesRuntimeHints implements RuntimeHintsRegistrar {
      public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
         BindableRuntimeHintsRegistrar.forTypes(ApplicationProperties.class).registerHints(hints, classLoader);
      }
   }
}
