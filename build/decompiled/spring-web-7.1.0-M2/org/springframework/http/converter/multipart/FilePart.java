package org.springframework.http.converter.multipart;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public interface FilePart extends Part {
   String filename();

   default void transferTo(File dest) throws IOException {
      this.transferTo(dest.toPath());
   }

   void transferTo(Path dest) throws IOException;
}
