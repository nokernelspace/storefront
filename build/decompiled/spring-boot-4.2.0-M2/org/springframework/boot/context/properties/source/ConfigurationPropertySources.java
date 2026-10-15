package org.springframework.boot.context.properties.source;

import java.util.Collections;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.ConfigurablePropertyResolver;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySources;
import org.springframework.core.env.PropertySource.StubPropertySource;
import org.springframework.lang.Contract;
import org.springframework.util.Assert;

public final class ConfigurationPropertySources {
   private static final String ATTACHED_PROPERTY_SOURCE_NAME = "configurationProperties";

   private ConfigurationPropertySources() {
   }

   public static ConfigurablePropertyResolver createPropertyResolver(MutablePropertySources propertySources) {
      return new ConfigurationPropertySourcesPropertyResolver(propertySources);
   }

   public static boolean isAttachedConfigurationPropertySource(PropertySource<?> propertySource) {
      return "configurationProperties".equals(propertySource.getName());
   }

   public static void attach(Environment environment) {
      Assert.isInstanceOf(ConfigurableEnvironment.class, environment);
      MutablePropertySources sources = ((ConfigurableEnvironment)environment).getPropertySources();
      PropertySource<?> attached = getAttached(sources);
      if (!isUsingSources(attached, sources)) {
         attached = new ConfigurationPropertySourcesPropertySource("configurationProperties", new SpringConfigurationPropertySources(sources));
      }

      sources.remove("configurationProperties");
      sources.addFirst(attached);
   }

   @Contract("null, _ -> false")
   private static boolean isUsingSources(@Nullable PropertySource<?> attached, MutablePropertySources sources) {
      return attached instanceof ConfigurationPropertySourcesPropertySource
         && ((SpringConfigurationPropertySources)attached.getSource()).isUsingSources(sources);
   }

   static @Nullable PropertySource<?> getAttached(@Nullable MutablePropertySources sources) {
      return sources != null ? sources.get("configurationProperties") : null;
   }

   public static Iterable<ConfigurationPropertySource> get(Environment environment) {
      Assert.isInstanceOf(ConfigurableEnvironment.class, environment);
      MutablePropertySources sources = ((ConfigurableEnvironment)environment).getPropertySources();
      ConfigurationPropertySourcesPropertySource attached = (ConfigurationPropertySourcesPropertySource)sources.get("configurationProperties");
      return attached == null ? from(sources) : (Iterable)attached.getSource();
   }

   public static Iterable<@Nullable ConfigurationPropertySource> from(PropertySource<?> source) {
      return Collections.singleton(ConfigurationPropertySource.from(source));
   }

   public static Iterable<ConfigurationPropertySource> from(Iterable<PropertySource<?>> sources) {
      return new SpringConfigurationPropertySources(sources);
   }

   private static Stream<PropertySource<?>> streamPropertySources(PropertySources sources) {
      return sources.stream().flatMap(ConfigurationPropertySources::flatten).filter(ConfigurationPropertySources::isIncluded);
   }

   private static Stream<PropertySource<?>> flatten(PropertySource<?> source) {
      return source.getSource() instanceof ConfigurableEnvironment configurableEnvironment
         ? streamPropertySources(configurableEnvironment.getPropertySources())
         : Stream.of(source);
   }

   private static boolean isIncluded(PropertySource<?> source) {
      return !(source instanceof StubPropertySource) && !(source instanceof ConfigurationPropertySourcesPropertySource);
   }
}
