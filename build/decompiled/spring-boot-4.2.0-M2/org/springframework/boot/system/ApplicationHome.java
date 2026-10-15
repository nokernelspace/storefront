package org.springframework.boot.system;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Enumeration;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

public class ApplicationHome {
   private final @Nullable File source;
   private final File dir;

   public ApplicationHome() {
      this(null);
   }

   public ApplicationHome(@Nullable Class<?> sourceClass) {
      this.source = this.findSource(sourceClass != null ? sourceClass : this.getStartClass());
      this.dir = this.findHomeDir(this.source);
   }

   private @Nullable Class<?> getStartClass() {
      try {
         ClassLoader classLoader = this.getClass().getClassLoader();
         return this.getStartClass(classLoader.getResources("META-INF/MANIFEST.MF"));
      } catch (Exception ex) {
         return null;
      }
   }

   private @Nullable Class<?> getStartClass(Enumeration<URL> manifestResources) {
      while (manifestResources.hasMoreElements()) {
         try (InputStream inputStream = manifestResources.nextElement().openStream()) {
            Manifest manifest = new Manifest(inputStream);
            String startClass = manifest.getMainAttributes().getValue("Start-Class");
            if (startClass != null) {
               return ClassUtils.forName(startClass, this.getClass().getClassLoader());
            }
         } catch (Exception var8) {
         }
      }

      return null;
   }

   private @Nullable File findSource(@Nullable Class<?> sourceClass) {
      try {
         ProtectionDomain domain = sourceClass != null ? sourceClass.getProtectionDomain() : null;
         CodeSource codeSource = domain != null ? domain.getCodeSource() : null;
         URL location = codeSource != null ? codeSource.getLocation() : null;
         File source = location != null ? this.findSource(location) : null;
         if (source != null && source.exists() && !this.isUnitTest()) {
            return source.getAbsoluteFile();
         }
      } catch (Exception var6) {
      }

      return null;
   }

   private boolean isUnitTest() {
      try {
         StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();

         for (int i = stackTrace.length - 1; i >= 0; i--) {
            if (stackTrace[i].getClassName().startsWith("org.junit.")) {
               return true;
            }
         }
      } catch (Exception var3) {
      }

      return false;
   }

   private File findSource(URL location) throws IOException, URISyntaxException {
      return location.openConnection() instanceof JarURLConnection jarURLConnection
         ? this.getRootJarFile(jarURLConnection.getJarFile())
         : new File(location.toURI());
   }

   private File getRootJarFile(JarFile jarFile) {
      String name = jarFile.getName();
      int separator = name.indexOf("!/");
      if (separator > 0) {
         name = name.substring(0, separator);
      }

      return new File(name);
   }

   private File findHomeDir(@Nullable File source) {
      File homeDir = source;
      homeDir = homeDir != null ? homeDir : this.findDefaultHomeDir();
      if (homeDir.isFile()) {
         homeDir = homeDir.getParentFile();
      }

      homeDir = homeDir.exists() ? homeDir : new File(".");
      return homeDir.getAbsoluteFile();
   }

   private File findDefaultHomeDir() {
      String userDir = System.getProperty("user.dir");
      return new File(StringUtils.hasLength(userDir) ? userDir : ".");
   }

   public @Nullable File getSource() {
      return this.source;
   }

   public @Nullable Path getSourcePath() {
      return this.source != null ? this.source.toPath() : null;
   }

   public File getDir() {
      return this.dir;
   }

   public Path getPath() {
      return this.dir.toPath();
   }

   @Override
   public String toString() {
      return this.getDir().toString();
   }
}
