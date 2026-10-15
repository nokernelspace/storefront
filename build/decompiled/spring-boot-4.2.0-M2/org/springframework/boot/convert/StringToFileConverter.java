package org.springframework.boot.convert;

import java.io.File;
import java.io.IOException;
import org.springframework.boot.io.ApplicationResourceLoader;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.io.Resource;

class StringToFileConverter implements Converter<String, File> {
   public File convert(String source) {
      return this.getFile(ApplicationResourceLoader.get().getResource(source));
   }

   private File getFile(Resource resource) {
      try {
         return resource.getFile();
      } catch (IOException ex) {
         throw new IllegalStateException("Could not retrieve file for " + resource + ": " + ex.getMessage());
      }
   }
}
