package org.springframework.boot.io;

import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

class ClassPathResourceFilePathResolver implements ApplicationResourceLoader.FilePathResolver {
   @Override
   public @Nullable String resolveFilePath(String location, Resource resource) {
      return resource instanceof ClassPathResource && !this.isClassPathUrl(location) ? location : null;
   }

   private boolean isClassPathUrl(String location) {
      return location.startsWith("classpath:");
   }
}
