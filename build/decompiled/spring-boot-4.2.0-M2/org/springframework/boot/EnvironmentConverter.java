package org.springframework.boot;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

final class EnvironmentConverter {
   private static final String CONFIGURABLE_WEB_ENVIRONMENT_CLASS = "org.springframework.web.context.ConfigurableWebEnvironment";
   private static final Set<String> SERVLET_ENVIRONMENT_SOURCE_NAMES;
   private final ClassLoader classLoader;

   EnvironmentConverter(ClassLoader classLoader) {
      this.classLoader = classLoader;
   }

   ConfigurableEnvironment convertEnvironmentIfNecessary(ConfigurableEnvironment environment, Class<? extends ConfigurableEnvironment> type) {
      return type.equals(environment.getClass()) ? environment : this.convertEnvironment(environment, type);
   }

   private ConfigurableEnvironment convertEnvironment(ConfigurableEnvironment environment, Class<? extends ConfigurableEnvironment> type) {
      ConfigurableEnvironment result = this.createEnvironment(type);
      result.setActiveProfiles(environment.getActiveProfiles());
      result.setConversionService(environment.getConversionService());
      this.copyPropertySources(environment, result);
      return result;
   }

   private ConfigurableEnvironment createEnvironment(Class<? extends ConfigurableEnvironment> type) {
      try {
         Constructor<? extends ConfigurableEnvironment> constructor = type.getDeclaredConstructor();
         ReflectionUtils.makeAccessible(constructor);
         return constructor.newInstance();
      } catch (Exception ex) {
         return new ApplicationEnvironment();
      }
   }

   private void copyPropertySources(ConfigurableEnvironment source, ConfigurableEnvironment target) {
      this.removePropertySources(target.getPropertySources(), this.isServletEnvironment(target.getClass(), this.classLoader));

      for (PropertySource<?> propertySource : source.getPropertySources()) {
         if (!SERVLET_ENVIRONMENT_SOURCE_NAMES.contains(propertySource.getName())) {
            target.getPropertySources().addLast(propertySource);
         }
      }
   }

   private boolean isServletEnvironment(Class<?> conversionType, ClassLoader classLoader) {
      try {
         Class<?> webEnvironmentClass = ClassUtils.forName("org.springframework.web.context.ConfigurableWebEnvironment", classLoader);
         return webEnvironmentClass.isAssignableFrom(conversionType);
      } catch (Throwable ex) {
         return false;
      }
   }

   private void removePropertySources(MutablePropertySources propertySources, boolean isServletEnvironment) {
      Set<String> names = new HashSet<>();

      for (PropertySource<?> propertySource : propertySources) {
         names.add(propertySource.getName());
      }

      for (String name : names) {
         if (!isServletEnvironment || !SERVLET_ENVIRONMENT_SOURCE_NAMES.contains(name)) {
            propertySources.remove(name);
         }
      }
   }

   static {
      Set<String> names = new HashSet<>();
      names.add("servletContextInitParams");
      names.add("servletConfigInitParams");
      names.add("jndiProperties");
      SERVLET_ENVIRONMENT_SOURCE_NAMES = Collections.unmodifiableSet(names);
   }
}
