package org.springframework.boot;

import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.env.PropertySourceInfo;
import org.springframework.boot.system.ApplicationPid;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.util.StringUtils;

class ApplicationInfoPropertySource extends MapPropertySource implements PropertySourceInfo {
   static final String NAME = "applicationInfo";

   ApplicationInfoPropertySource(@Nullable Class<?> mainClass) {
      super("applicationInfo", getProperties(readVersion(mainClass)));
   }

   ApplicationInfoPropertySource(@Nullable String applicationVersion) {
      super("applicationInfo", getProperties(applicationVersion));
   }

   @Override
   public boolean isImmutable() {
      return true;
   }

   private static Map<String, Object> getProperties(@Nullable String applicationVersion) {
      Map<String, Object> result = new HashMap<>();
      if (StringUtils.hasText(applicationVersion)) {
         result.put("spring.application.version", applicationVersion);
      }

      ApplicationPid applicationPid = new ApplicationPid();
      Long pid = applicationPid.toLong();
      if (pid != null) {
         result.put("spring.application.pid", pid);
      }

      return result;
   }

   private static @Nullable String readVersion(@Nullable Class<?> applicationClass) {
      Package sourcePackage = applicationClass != null ? applicationClass.getPackage() : null;
      return sourcePackage != null ? sourcePackage.getImplementationVersion() : null;
   }

   static void moveToEnd(ConfigurableEnvironment environment) {
      MutablePropertySources propertySources = environment.getPropertySources();
      PropertySource<?> propertySource = propertySources.remove("applicationInfo");
      if (propertySource != null) {
         propertySources.addLast(propertySource);
      }
   }
}
