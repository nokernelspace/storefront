package org.springframework.web.multipart;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;

public interface MultipartFile extends InputStreamSource {
   String getName();

   @Nullable String getOriginalFilename();

   @Nullable String getContentType();

   boolean isEmpty();

   long getSize();

   byte[] getBytes() throws IOException;

   InputStream getInputStream() throws IOException;

   default Resource getResource() {
      return new MultipartFileResource(this);
   }

   void transferTo(File dest) throws IOException, IllegalStateException;

   default void transferTo(Path dest) throws IOException, IllegalStateException {
      FileCopyUtils.copy(this.getInputStream(), Files.newOutputStream(dest));
   }
}
