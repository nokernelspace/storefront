package org.springframework.core.io;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public class ClassPathResource extends AbstractFileResolvingResource {
   private final String path;
   private final String absolutePath;
   private final @Nullable ClassLoader classLoader;
   private final @Nullable Class<?> clazz;

   public ClassPathResource(String path) {
      this(path, (ClassLoader)null);
   }

   public ClassPathResource(String path, @Nullable ClassLoader classLoader) {
      Assert.notNull(path, "Path must not be null");
      String pathToUse = StringUtils.cleanPath(path);
      if (pathToUse.startsWith("/")) {
         pathToUse = pathToUse.substring(1);
      }

      this.path = pathToUse;
      this.absolutePath = pathToUse;
      this.classLoader = classLoader != null ? classLoader : ClassUtils.getDefaultClassLoader();
      this.clazz = null;
   }

   public ClassPathResource(String path, @Nullable Class<?> clazz) {
      Assert.notNull(path, "Path must not be null");
      this.path = StringUtils.cleanPath(path);
      String absolutePath = this.path;
      if (clazz != null && !absolutePath.startsWith("/")) {
         absolutePath = ClassUtils.classPackageAsResourcePath(clazz) + "/" + absolutePath;
      } else if (absolutePath.startsWith("/")) {
         absolutePath = absolutePath.substring(1);
      }

      this.absolutePath = absolutePath;
      this.classLoader = null;
      this.clazz = clazz;
   }

   public final String getPath() {
      return this.absolutePath;
   }

   public final @Nullable ClassLoader getClassLoader() {
      return this.clazz != null ? this.clazz.getClassLoader() : this.classLoader;
   }

   @Override
   public boolean exists() {
      return this.resolveURL() != null;
   }

   @Override
   public boolean isReadable() {
      URL url = this.resolveURL();
      return url != null && this.checkReadable(url);
   }

   protected @Nullable URL resolveURL() {
      try {
         if (this.clazz != null) {
            return this.clazz.getResource(this.path);
         } else {
            return this.classLoader != null ? this.classLoader.getResource(this.absolutePath) : ClassLoader.getSystemResource(this.absolutePath);
         }
      } catch (IllegalArgumentException ex) {
         return null;
      }
   }

   @Override
   public InputStream getInputStream() throws IOException {
      InputStream is;
      if (this.clazz != null) {
         is = this.clazz.getResourceAsStream(this.path);
      } else if (this.classLoader != null) {
         is = this.classLoader.getResourceAsStream(this.absolutePath);
      } else {
         is = ClassLoader.getSystemResourceAsStream(this.absolutePath);
      }

      if (is == null) {
         throw new FileNotFoundException(this.getDescription() + " cannot be opened because it does not exist");
      } else {
         return is;
      }
   }

   @Override
   public URL getURL() throws IOException {
      URL url = this.resolveURL();
      if (url == null) {
         throw new FileNotFoundException(this.getDescription() + " cannot be resolved to URL because it does not exist");
      } else {
         return url;
      }
   }

   @Override
   public Resource createRelative(String relativePath) {
      String pathToUse = StringUtils.applyRelativePath(this.path, relativePath);
      return this.clazz != null ? new ClassPathResource(pathToUse, this.clazz) : new ClassPathResource(pathToUse, this.classLoader);
   }

   @Override
   public @Nullable String getFilename() {
      return StringUtils.getFilename(this.absolutePath);
   }

   @Override
   public String getDescription() {
      return "class path resource [" + this.absolutePath + "]";
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof ClassPathResource that
            && this.absolutePath.equals(that.absolutePath)
            && ObjectUtils.nullSafeEquals(this.getClassLoader(), that.getClassLoader());
   }

   @Override
   public int hashCode() {
      return this.absolutePath.hashCode();
   }
}
