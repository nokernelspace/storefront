package org.springframework.core.io;

public class FileSystemResourceLoader extends DefaultResourceLoader {
   @Override
   protected Resource getResourceByPath(String path) {
      if (path.startsWith("/")) {
         path = path.substring(1);
      }

      return new FileSystemResourceLoader.FileSystemContextResource(path);
   }

   private static class FileSystemContextResource extends FileSystemResource implements ContextResource {
      public FileSystemContextResource(String path) {
         super(path);
      }

      @Override
      public String getPathWithinContext() {
         return this.getPath();
      }
   }
}
