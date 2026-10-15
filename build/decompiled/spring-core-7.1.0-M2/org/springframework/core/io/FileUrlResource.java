package org.springframework.core.io;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import org.jspecify.annotations.Nullable;

public class FileUrlResource extends UrlResource implements WritableResource {
   private volatile @Nullable File file;

   public FileUrlResource(URL url) {
      super(url);
   }

   public FileUrlResource(String location) throws MalformedURLException {
      super("file", location);
   }

   @Override
   public File getFile() throws IOException {
      File file = this.file;
      if (file != null) {
         return file;
      }

      file = super.getFile();
      this.file = file;
      return file;
   }

   @Override
   public boolean isWritable() {
      try {
         File file = this.getFile();
         return file.canWrite() && !file.isDirectory();
      } catch (IOException ex) {
         return false;
      }
   }

   @Override
   public OutputStream getOutputStream() throws IOException {
      return Files.newOutputStream(this.getFile().toPath());
   }

   @Override
   public WritableByteChannel writableChannel() throws IOException {
      return FileChannel.open(this.getFile().toPath(), StandardOpenOption.WRITE);
   }

   @Override
   public Resource createRelative(String relativePath) throws MalformedURLException {
      FileUrlResource resource = new FileUrlResource(this.createRelativeURL(relativePath));
      resource.useCaches = this.useCaches;
      return resource;
   }
}
