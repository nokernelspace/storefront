package org.springframework.core.env;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.SpringProperties;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public abstract class AbstractEnvironment implements ConfigurableEnvironment {
   public static final String IGNORE_GETENV_PROPERTY_NAME = "spring.getenv.ignore";
   public static final String ACTIVE_PROFILES_PROPERTY_NAME = "spring.profiles.active";
   public static final String DEFAULT_PROFILES_PROPERTY_NAME = "spring.profiles.default";
   public static final String RESERVED_DEFAULT_PROFILE_NAME = "default";
   protected final Log logger = LogFactory.getLog(this.getClass());
   private final Set<String> activeProfiles = new LinkedHashSet<>();
   private final Set<String> defaultProfiles = new LinkedHashSet<>(this.getReservedDefaultProfiles());
   private final MutablePropertySources propertySources;
   private final ConfigurablePropertyResolver propertyResolver;

   public AbstractEnvironment() {
      this(new MutablePropertySources());
   }

   protected AbstractEnvironment(MutablePropertySources propertySources) {
      this.propertySources = propertySources;
      this.propertyResolver = this.createPropertyResolver(propertySources);
      this.customizePropertySources(propertySources);
   }

   protected ConfigurablePropertyResolver createPropertyResolver(MutablePropertySources propertySources) {
      return new PropertySourcesPropertyResolver(propertySources);
   }

   protected final ConfigurablePropertyResolver getPropertyResolver() {
      return this.propertyResolver;
   }

   protected void customizePropertySources(MutablePropertySources propertySources) {
   }

   protected Set<String> getReservedDefaultProfiles() {
      return Collections.singleton("default");
   }

   @Override
   public String[] getActiveProfiles() {
      return StringUtils.toStringArray(this.doGetActiveProfiles());
   }

   protected Set<String> doGetActiveProfiles() {
      synchronized (this.activeProfiles) {
         if (this.activeProfiles.isEmpty()) {
            String profiles = this.doGetActiveProfilesProperty();
            if (StringUtils.hasText(profiles)) {
               this.setActiveProfiles(StringUtils.commaDelimitedListToStringArray(StringUtils.trimAllWhitespace(profiles)));
            }
         }

         return this.activeProfiles;
      }
   }

   protected @Nullable String doGetActiveProfilesProperty() {
      return this.getProperty("spring.profiles.active");
   }

   @Override
   public void setActiveProfiles(String... profiles) {
      Assert.notNull(profiles, "Profile array must not be null");
      if (this.logger.isDebugEnabled()) {
         this.logger.debug("Activating profiles " + Arrays.toString(profiles));
      }

      synchronized (this.activeProfiles) {
         this.activeProfiles.clear();

         for (String profile : profiles) {
            this.validateProfile(profile);
            this.activeProfiles.add(profile);
         }
      }
   }

   @Override
   public void addActiveProfile(String profile) {
      if (this.logger.isDebugEnabled()) {
         this.logger.debug("Activating profile '" + profile + "'");
      }

      this.validateProfile(profile);
      this.doGetActiveProfiles();
      synchronized (this.activeProfiles) {
         this.activeProfiles.add(profile);
      }
   }

   @Override
   public String[] getDefaultProfiles() {
      return StringUtils.toStringArray(this.doGetDefaultProfiles());
   }

   protected Set<String> doGetDefaultProfiles() {
      synchronized (this.defaultProfiles) {
         if (this.defaultProfiles.equals(this.getReservedDefaultProfiles())) {
            String profiles = this.doGetDefaultProfilesProperty();
            if (StringUtils.hasText(profiles)) {
               this.setDefaultProfiles(StringUtils.commaDelimitedListToStringArray(StringUtils.trimAllWhitespace(profiles)));
            }
         }

         return this.defaultProfiles;
      }
   }

   protected @Nullable String doGetDefaultProfilesProperty() {
      return this.getProperty("spring.profiles.default");
   }

   @Override
   public void setDefaultProfiles(String... profiles) {
      Assert.notNull(profiles, "Profile array must not be null");
      synchronized (this.defaultProfiles) {
         this.defaultProfiles.clear();

         for (String profile : profiles) {
            this.validateProfile(profile);
            this.defaultProfiles.add(profile);
         }
      }
   }

   @Deprecated(since = "5.1")
   @Override
   public boolean acceptsProfiles(String... profiles) {
      Assert.notEmpty(profiles, "Must specify at least one profile");

      for (String profile : profiles) {
         if (StringUtils.hasLength(profile) && profile.charAt(0) == '!') {
            if (!this.isProfileActive(profile.substring(1))) {
               return true;
            }
         } else if (this.isProfileActive(profile)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public boolean acceptsProfiles(Profiles profiles) {
      Assert.notNull(profiles, "Profiles must not be null");
      return profiles.matches(this::isProfileActive);
   }

   protected boolean isProfileActive(String profile) {
      this.validateProfile(profile);
      Set<String> currentActiveProfiles = this.doGetActiveProfiles();
      return currentActiveProfiles.contains(profile) || currentActiveProfiles.isEmpty() && this.doGetDefaultProfiles().contains(profile);
   }

   protected void validateProfile(String profile) {
      if (!StringUtils.hasText(profile)) {
         throw new IllegalArgumentException("Invalid profile [" + profile + "]: must contain text");
      }

      if (profile.charAt(0) == '!') {
         throw new IllegalArgumentException("Invalid profile [" + profile + "]: must not begin with ! operator");
      }
   }

   @Override
   public MutablePropertySources getPropertySources() {
      return this.propertySources;
   }

   @Override
   public Map<String, Object> getSystemProperties() {
      return System.getProperties();
   }

   @Override
   public Map<String, Object> getSystemEnvironment() {
      return this.suppressGetenvAccess() ? Collections.emptyMap() : System.getenv();
   }

   protected boolean suppressGetenvAccess() {
      return SpringProperties.getFlag("spring.getenv.ignore");
   }

   @Override
   public void merge(ConfigurableEnvironment parent) {
      for (PropertySource<?> ps : parent.getPropertySources()) {
         if (!this.propertySources.contains(ps.getName())) {
            this.propertySources.addLast(ps);
         }
      }

      String[] parentActiveProfiles = parent.getActiveProfiles();
      if (!ObjectUtils.isEmpty(parentActiveProfiles)) {
         synchronized (this.activeProfiles) {
            Collections.addAll(this.activeProfiles, parentActiveProfiles);
         }
      }

      String[] parentDefaultProfiles = parent.getDefaultProfiles();
      if (!ObjectUtils.isEmpty(parentDefaultProfiles)) {
         synchronized (this.defaultProfiles) {
            this.defaultProfiles.remove("default");
            Collections.addAll(this.defaultProfiles, parentDefaultProfiles);
         }
      }
   }

   @Override
   public ConfigurableConversionService getConversionService() {
      return this.propertyResolver.getConversionService();
   }

   @Override
   public void setConversionService(ConfigurableConversionService conversionService) {
      this.propertyResolver.setConversionService(conversionService);
   }

   @Override
   public void setPlaceholderPrefix(String placeholderPrefix) {
      this.propertyResolver.setPlaceholderPrefix(placeholderPrefix);
   }

   @Override
   public void setPlaceholderSuffix(String placeholderSuffix) {
      this.propertyResolver.setPlaceholderSuffix(placeholderSuffix);
   }

   @Override
   public void setValueSeparator(@Nullable String valueSeparator) {
      this.propertyResolver.setValueSeparator(valueSeparator);
   }

   @Override
   public void setEscapeCharacter(@Nullable Character escapeCharacter) {
      this.propertyResolver.setEscapeCharacter(escapeCharacter);
   }

   @Override
   public void setIgnoreUnresolvableNestedPlaceholders(boolean ignoreUnresolvableNestedPlaceholders) {
      this.propertyResolver.setIgnoreUnresolvableNestedPlaceholders(ignoreUnresolvableNestedPlaceholders);
   }

   @Override
   public void setRequiredProperties(String... requiredProperties) {
      this.propertyResolver.setRequiredProperties(requiredProperties);
   }

   @Override
   public void validateRequiredProperties() throws MissingRequiredPropertiesException {
      this.propertyResolver.validateRequiredProperties();
   }

   @Override
   public boolean containsProperty(String key) {
      return this.propertyResolver.containsProperty(key);
   }

   @Override
   public @Nullable String getProperty(String key) {
      return this.propertyResolver.getProperty(key);
   }

   @Override
   public String getProperty(String key, String defaultValue) {
      return this.propertyResolver.getProperty(key, defaultValue);
   }

   @Override
   public <T> @Nullable T getProperty(String key, Class<T> targetType) {
      return this.propertyResolver.getProperty(key, targetType);
   }

   @Override
   public <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
      return this.propertyResolver.getProperty(key, targetType, defaultValue);
   }

   @Override
   public String getRequiredProperty(String key) throws IllegalStateException {
      return this.propertyResolver.getRequiredProperty(key);
   }

   @Override
   public <T> T getRequiredProperty(String key, Class<T> targetType) throws IllegalStateException {
      return this.propertyResolver.getRequiredProperty(key, targetType);
   }

   @Override
   public String resolvePlaceholders(String text) {
      return this.propertyResolver.resolvePlaceholders(text);
   }

   @Override
   public String resolveRequiredPlaceholders(String text) throws IllegalArgumentException {
      return this.propertyResolver.resolveRequiredPlaceholders(text);
   }

   @Override
   public String toString() {
      return this.getClass().getSimpleName()
         + " {activeProfiles="
         + this.activeProfiles
         + ", defaultProfiles="
         + this.defaultProfiles
         + ", propertySources="
         + this.propertySources
         + "}";
   }
}
