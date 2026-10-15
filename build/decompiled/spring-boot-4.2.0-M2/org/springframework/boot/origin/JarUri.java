package org.springframework.boot.origin;

import java.net.URI;
import org.jspecify.annotations.Nullable;

final class JarUri {
   private static final String JAR_SCHEME = "jar:";
   private static final String JAR_EXTENSION = ".jar";
   private final String uri;
   private final String description;

   private JarUri(String uri) {
      this.uri = uri;
      this.description = this.extractDescription(uri);
   }

   private String extractDescription(String uri) {
      uri = uri.substring("jar:".length());
      int firstDotJar = uri.indexOf(".jar");
      String firstJar = this.getFilename(uri.substring(0, firstDotJar + ".jar".length()));
      uri = uri.substring(firstDotJar + ".jar".length());
      int lastDotJar = uri.lastIndexOf(".jar");
      return lastDotJar == -1 ? firstJar : firstJar + uri.substring(0, lastDotJar + ".jar".length());
   }

   private String getFilename(String string) {
      int lastSlash = string.lastIndexOf(47);
      return lastSlash == -1 ? string : string.substring(lastSlash + 1);
   }

   String getDescription() {
      return this.description;
   }

   String getDescription(String existing) {
      return existing + " from " + this.description;
   }

   @Override
   public String toString() {
      return this.uri;
   }

   static @Nullable JarUri from(URI uri) {
      return from(uri.toString());
   }

   static @Nullable JarUri from(String uri) {
      return uri.startsWith("jar:") && uri.contains(".jar") ? new JarUri(uri) : null;
   }
}
