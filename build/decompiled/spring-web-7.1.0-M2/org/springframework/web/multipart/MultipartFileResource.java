package org.springframework.web.multipart;

import java.io.IOException;
import java.io.InputStream;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.AbstractResource;
import org.springframework.util.Assert;

class MultipartFileResource extends AbstractResource {
   private final MultipartFile multipartFile;

   public MultipartFileResource(MultipartFile multipartFile) {
      Assert.notNull(multipartFile, "MultipartFile must not be null");
      this.multipartFile = multipartFile;
   }

   public boolean exists() {
      return true;
   }

   public boolean isOpen() {
      return true;
   }

   public long contentLength() {
      return this.multipartFile.getSize();
   }

   public @Nullable String getFilename() {
      return this.multipartFile.getOriginalFilename();
   }

   public InputStream getInputStream() throws IOException, IllegalStateException {
      return this.multipartFile.getInputStream();
   }

   public String getDescription() {
      return "MultipartFile resource [" + this.multipartFile.getName() + "]";
   }

   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof MultipartFileResource that && this.multipartFile.equals(that.multipartFile);
   }

   public int hashCode() {
      return this.multipartFile.hashCode();
   }
}
