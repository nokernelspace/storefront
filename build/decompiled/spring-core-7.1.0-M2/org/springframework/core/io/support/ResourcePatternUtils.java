package org.springframework.core.io.support;

import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ResourceUtils;

public abstract class ResourcePatternUtils {
   public static boolean isUrl(@Nullable String resourceLocation) {
      return resourceLocation != null && (resourceLocation.startsWith("classpath*:") || ResourceUtils.isUrl(resourceLocation));
   }

   public static ResourcePatternResolver getResourcePatternResolver(@Nullable ResourceLoader resourceLoader) {
      if (resourceLoader instanceof ResourcePatternResolver resolver) {
         return resolver;
      } else {
         return resourceLoader != null ? new PathMatchingResourcePatternResolver(resourceLoader) : new PathMatchingResourcePatternResolver();
      }
   }
}
