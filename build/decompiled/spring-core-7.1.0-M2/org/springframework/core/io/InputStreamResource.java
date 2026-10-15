package org.springframework.core.io;

import java.io.IOException;
import java.io.InputStream;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public class InputStreamResource extends AbstractResource {
   private final InputStreamSource inputStreamSource;
   private final String description;
   private final Object equality;
   private boolean read = false;

   public InputStreamResource(InputStreamSource inputStreamSource) {
      this(inputStreamSource, "resource loaded from InputStreamSource");
   }

   public InputStreamResource(InputStreamSource inputStreamSource, @Nullable String description) {
      Assert.notNull(inputStreamSource, "InputStreamSource must not be null");
      this.inputStreamSource = inputStreamSource;
      this.description = description != null ? description : "";
      this.equality = inputStreamSource;
   }

   public InputStreamResource(InputStream inputStream) {
      this(inputStream, "resource loaded through InputStream");
   }

   public InputStreamResource(InputStream inputStream, @Nullable String description) {
      Assert.notNull(inputStream, "InputStream must not be null");
      this.inputStreamSource = () -> inputStream;
      this.description = description != null ? description : "";
      this.equality = inputStream;
   }

   @Override
   public boolean exists() {
      return true;
   }

   @Override
   public boolean isOpen() {
      return true;
   }

   @Override
   public InputStream getInputStream() throws IOException, IllegalStateException {
      if (this.read) {
         throw new IllegalStateException(
            "InputStream has already been read (possibly for early content length determination) - do not use InputStreamResource if a stream needs to be read multiple times"
         );
      }

      this.read = true;
      return this.inputStreamSource.getInputStream();
   }

   @Override
   public String getDescription() {
      return "InputStream resource [" + this.description + "]";
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof InputStreamResource that && this.equality.equals(that.equality);
   }

   @Override
   public int hashCode() {
      return this.equality.hashCode();
   }
}
