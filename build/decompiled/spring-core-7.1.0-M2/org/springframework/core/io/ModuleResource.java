package org.springframework.core.io;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class ModuleResource extends AbstractResource {
   private final Module module;
   private final String path;

   public ModuleResource(Module module, String path) {
      Assert.notNull(module, "Module must not be null");
      Assert.notNull(path, "Path must not be null");
      this.module = module;
      this.path = path;
   }

   public final Module getModule() {
      return this.module;
   }

   public final String getPath() {
      return this.path;
   }

   @Override
   public InputStream getInputStream() throws IOException {
      InputStream is = this.module.getResourceAsStream(this.path);
      if (is == null) {
         throw new FileNotFoundException(this.getDescription() + " cannot be opened because it does not exist");
      } else {
         return is;
      }
   }

   @Override
   public Resource createRelative(String relativePath) {
      String pathToUse = StringUtils.applyRelativePath(this.path, relativePath);
      return new ModuleResource(this.module, pathToUse);
   }

   @Override
   public @Nullable String getFilename() {
      return StringUtils.getFilename(this.path);
   }

   @Override
   public String getDescription() {
      return "module resource [" + this.path + "]" + (this.module.isNamed() ? " from module [" + this.module.getName() + "]" : "");
   }

   @Override
   public boolean equals(@Nullable Object obj) {
      return this == obj || obj instanceof ModuleResource that && this.module.equals(that.module) && this.path.equals(that.path);
   }

   @Override
   public int hashCode() {
      return this.module.hashCode() * 31 + this.path.hashCode();
   }
}
