package org.springframework.boot.context.config;

import java.io.File;
import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.FileUrlResource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

public class StandardConfigDataResource extends ConfigDataResource {
   private final StandardConfigDataReference reference;
   private final Resource resource;
   private final boolean emptyDirectory;

   StandardConfigDataResource(StandardConfigDataReference reference, Resource resource) {
      this(reference, resource, false);
   }

   StandardConfigDataResource(StandardConfigDataReference reference, Resource resource, boolean emptyDirectory) {
      Assert.notNull(reference, "'reference' must not be null");
      Assert.notNull(resource, "'resource' must not be null");
      this.reference = reference;
      this.resource = resource;
      this.emptyDirectory = emptyDirectory;
   }

   StandardConfigDataReference getReference() {
      return this.reference;
   }

   public Resource getResource() {
      return this.resource;
   }

   public @Nullable String getProfile() {
      return this.reference.getProfile();
   }

   boolean isEmptyDirectory() {
      return this.emptyDirectory;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (obj != null && this.getClass() == obj.getClass()) {
         StandardConfigDataResource other = (StandardConfigDataResource)obj;
         return this.emptyDirectory == other.emptyDirectory && this.isSameUnderlyingResource(this.resource, other.resource);
      } else {
         return false;
      }
   }

   private boolean isSameUnderlyingResource(Resource ours, Resource other) {
      return ours.equals(other) || this.isSameFile(this.getUnderlyingFile(ours), this.getUnderlyingFile(other));
   }

   private boolean isSameFile(@Nullable File ours, @Nullable File other) {
      return ours != null && ours.equals(other);
   }

   @Override
   public int hashCode() {
      File underlyingFile = this.getUnderlyingFile(this.resource);
      return underlyingFile != null ? underlyingFile.hashCode() : this.resource.hashCode();
   }

   @Override
   public String toString() {
      if (this.resource instanceof FileSystemResource || this.resource instanceof FileUrlResource) {
         try {
            return "file [" + this.resource.getFile() + "]";
         } catch (IOException var2) {
         }
      }

      return this.resource.toString();
   }

   private @Nullable File getUnderlyingFile(Resource resource) {
      try {
         if (resource instanceof ClassPathResource || resource instanceof FileSystemResource || resource instanceof FileUrlResource) {
            return resource.getFile().getAbsoluteFile();
         }
      } catch (IOException var3) {
      }

      return null;
   }
}
