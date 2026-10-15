package org.springframework.boot.web.context.servlet;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.io.ApplicationResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.util.ClassUtils;

class ServletContextResourceFilePathResolver implements ApplicationResourceLoader.FilePathResolver {
   private static final String RESOURCE_CLASS_NAME = "org.springframework.web.context.support.ServletContextResource";
   private final @Nullable Class<?> resourceClass;

   ServletContextResourceFilePathResolver() {
      ClassLoader classLoader = this.getClass().getClassLoader();
      this.resourceClass = ClassUtils.isPresent("org.springframework.web.context.support.ServletContextResource", classLoader)
         ? ClassUtils.resolveClassName("org.springframework.web.context.support.ServletContextResource", classLoader)
         : null;
   }

   @Override
   public @Nullable String resolveFilePath(String location, Resource resource) {
      return this.resourceClass != null && this.resourceClass.isInstance(resource) ? location : null;
   }
}
