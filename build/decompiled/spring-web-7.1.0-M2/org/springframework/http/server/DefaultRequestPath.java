package org.springframework.http.server;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

class DefaultRequestPath implements RequestPath {
   private final PathContainer fullPath;
   private final PathContainer contextPath;
   private final PathContainer pathWithinApplication;

   DefaultRequestPath(String rawPath, @Nullable String contextPath) {
      this.fullPath = PathContainer.parsePath(rawPath);
      this.contextPath = initContextPath(this.fullPath, contextPath);
      this.pathWithinApplication = extractPathWithinApplication(this.fullPath, this.contextPath);
   }

   private DefaultRequestPath(RequestPath requestPath, String contextPath) {
      this.fullPath = requestPath;
      this.contextPath = initContextPath(this.fullPath, contextPath);
      this.pathWithinApplication = extractPathWithinApplication(this.fullPath, this.contextPath);
   }

   private static PathContainer initContextPath(PathContainer path, @Nullable String contextPath) {
      if (StringUtils.hasText(contextPath) && !StringUtils.matchesCharacter(contextPath, '/')) {
         validateContextPath(path.value(), contextPath);
         int length = contextPath.length();
         int counter = 0;

         for (int i = 0; i < path.elements().size(); i++) {
            PathContainer.Element element = path.elements().get(i);
            counter += element.value().length();
            if (length == counter) {
               return path.subPath(0, i + 1);
            }
         }

         throw new IllegalStateException("Failed to initialize contextPath '" + contextPath + "' for requestPath '" + path.value() + "'");
      } else {
         return PathContainer.parsePath("");
      }
   }

   private static void validateContextPath(String fullPath, String contextPath) {
      int length = contextPath.length();
      if (contextPath.charAt(0) != '/' || contextPath.charAt(length - 1) == '/') {
         throw new IllegalArgumentException("Invalid contextPath: '" + contextPath + "': must start with '/' and not end with '/'");
      }

      if (!fullPath.startsWith(contextPath)) {
         throw new IllegalArgumentException("Invalid contextPath '" + contextPath + "': must match the start of requestPath: '" + fullPath + "'");
      }

      if (fullPath.length() > length && fullPath.charAt(length) != '/') {
         throw new IllegalArgumentException("Invalid contextPath '" + contextPath + "': must match to full path segments for requestPath: '" + fullPath + "'");
      }
   }

   private static PathContainer extractPathWithinApplication(PathContainer fullPath, PathContainer contextPath) {
      return fullPath.subPath(contextPath.elements().size());
   }

   @Override
   public String value() {
      return this.fullPath.value();
   }

   @Override
   public List<PathContainer.Element> elements() {
      return this.fullPath.elements();
   }

   @Override
   public PathContainer contextPath() {
      return this.contextPath;
   }

   @Override
   public PathContainer pathWithinApplication() {
      return this.pathWithinApplication;
   }

   @Override
   public RequestPath modifyContextPath(String contextPath) {
      return new DefaultRequestPath(this, contextPath);
   }

   @Override
   public boolean equals(@Nullable Object other) {
      if (this == other) {
         return true;
      } else if (other != null && this.getClass() == other.getClass()) {
         DefaultRequestPath otherPath = (DefaultRequestPath)other;
         return this.fullPath.equals(otherPath.fullPath)
            && this.contextPath.equals(otherPath.contextPath)
            && this.pathWithinApplication.equals(otherPath.pathWithinApplication);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      int result = this.fullPath.hashCode();
      result = 31 * result + this.contextPath.hashCode();
      return 31 * result + this.pathWithinApplication.hashCode();
   }

   @Override
   public String toString() {
      return this.fullPath.toString();
   }
}
