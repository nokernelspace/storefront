package org.springframework.boot.web.context.reactive;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

class FilteredReactiveWebContextResource extends AbstractResource {
   private final String path;

   FilteredReactiveWebContextResource(String path) {
      this.path = path;
   }

   public boolean exists() {
      return false;
   }

   public Resource createRelative(String relativePath) throws IOException {
      String pathToUse = StringUtils.applyRelativePath(this.path, relativePath);
      return new FilteredReactiveWebContextResource(pathToUse);
   }

   public String getDescription() {
      return "ReactiveWebContext resource [" + this.path + "]";
   }

   public InputStream getInputStream() throws IOException {
      throw new FileNotFoundException(this.getDescription() + " cannot be opened because it does not exist");
   }
}
