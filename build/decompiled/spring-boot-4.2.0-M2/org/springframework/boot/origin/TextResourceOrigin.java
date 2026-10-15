package org.springframework.boot.origin;

import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.ObjectUtils;

public class TextResourceOrigin implements Origin {
   private final @Nullable Resource resource;
   private final TextResourceOrigin.@Nullable Location location;

   public TextResourceOrigin(@Nullable Resource resource, TextResourceOrigin.@Nullable Location location) {
      this.resource = resource;
      this.location = location;
   }

   public @Nullable Resource getResource() {
      return this.resource;
   }

   public TextResourceOrigin.@Nullable Location getLocation() {
      return this.location;
   }

   @Override
   public @Nullable Origin getParent() {
      return Origin.from(this.resource);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (obj == null) {
         return false;
      } else if (!(obj instanceof TextResourceOrigin other)) {
         return super.equals(obj);
      } else {
         boolean result = true;
         result = result && ObjectUtils.nullSafeEquals(this.resource, other.resource);
         return result && ObjectUtils.nullSafeEquals(this.location, other.location);
      }
   }

   @Override
   public int hashCode() {
      int result = 1;
      result = 31 * result + ObjectUtils.nullSafeHashCode(this.resource);
      return 31 * result + ObjectUtils.nullSafeHashCode(this.location);
   }

   @Override
   public String toString() {
      StringBuilder result = new StringBuilder();
      result.append(this.getResourceDescription(this.resource));
      if (this.location != null) {
         result.append(" - ").append(this.location);
      }

      return result.toString();
   }

   private String getResourceDescription(@Nullable Resource resource) {
      if (resource instanceof OriginTrackedResource originTrackedResource) {
         return this.getResourceDescription(originTrackedResource.getResource());
      } else if (resource == null) {
         return "unknown resource [?]";
      } else {
         return resource instanceof ClassPathResource classPathResource ? this.getResourceDescription(classPathResource) : resource.getDescription();
      }
   }

   private String getResourceDescription(ClassPathResource resource) {
      try {
         JarUri jarUri = JarUri.from(resource.getURI());
         if (jarUri != null) {
            return jarUri.getDescription(resource.getDescription());
         }
      } catch (IOException var3) {
      }

      return resource.getDescription();
   }

   public static final class Location {
      private final int line;
      private final int column;

      public Location(int line, int column) {
         this.line = line;
         this.column = column;
      }

      public int getLine() {
         return this.line;
      }

      public int getColumn() {
         return this.column;
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         } else if (obj != null && this.getClass() == obj.getClass()) {
            TextResourceOrigin.Location other = (TextResourceOrigin.Location)obj;
            boolean result = true;
            result = result && this.line == other.line;
            return result && this.column == other.column;
         } else {
            return false;
         }
      }

      @Override
      public int hashCode() {
         return 31 * this.line + this.column;
      }

      @Override
      public String toString() {
         return this.line + 1 + ":" + (this.column + 1);
      }
   }
}
