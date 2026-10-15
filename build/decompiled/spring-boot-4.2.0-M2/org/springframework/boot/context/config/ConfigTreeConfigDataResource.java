package org.springframework.boot.context.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import org.springframework.util.Assert;

public class ConfigTreeConfigDataResource extends ConfigDataResource {
   private final Path path;

   ConfigTreeConfigDataResource(String path) {
      Assert.notNull(path, "'path' must not be null");
      this.path = Paths.get(path).toAbsolutePath();
   }

   ConfigTreeConfigDataResource(Path path) {
      Assert.notNull(path, "'path' must not be null");
      this.path = path.toAbsolutePath();
   }

   Path getPath() {
      return this.path;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (obj != null && this.getClass() == obj.getClass()) {
         ConfigTreeConfigDataResource other = (ConfigTreeConfigDataResource)obj;
         return Objects.equals(this.path, other.path);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return this.path.hashCode();
   }

   @Override
   public String toString() {
      return "config tree [" + this.path + "]";
   }
}
