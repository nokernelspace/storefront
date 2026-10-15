package org.springframework.core.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

@Deprecated(since = "7.0", forRemoval = true)
public class PathResource extends AbstractResource implements WritableResource {
   private final Path path;

   public PathResource(Path path) {
      Assert.notNull(path, "Path must not be null");
      this.path = path.normalize();
   }

   public PathResource(String path) {
      Assert.notNull(path, "Path must not be null");
      this.path = Paths.get(path).normalize();
   }

   public PathResource(URI uri) {
      Assert.notNull(uri, "URI must not be null");
      this.path = Paths.get(uri).normalize();
   }

   public final String getPath() {
      return this.path.toString();
   }

   @Override
   public boolean exists() {
      return Files.exists(this.path);
   }

   @Override
   public boolean isReadable() {
      return Files.isReadable(this.path) && !Files.isDirectory(this.path);
   }

   @Override
   public InputStream getInputStream() throws IOException {
      if (!this.exists()) {
         throw new FileNotFoundException(this.getPath() + " (no such file or directory)");
      } else if (Files.isDirectory(this.path)) {
         throw new FileNotFoundException(this.getPath() + " (is a directory)");
      } else {
         return Files.newInputStream(this.path);
      }
   }

   @Override
   public byte[] getContentAsByteArray() throws IOException {
      try {
         return Files.readAllBytes(this.path);
      } catch (NoSuchFileException ex) {
         throw new FileNotFoundException(ex.getMessage());
      }
   }

   @Override
   public String getContentAsString(Charset charset) throws IOException {
      try {
         return Files.readString(this.path, charset);
      } catch (NoSuchFileException ex) {
         throw new FileNotFoundException(ex.getMessage());
      }
   }

   @Override
   public boolean isWritable() {
      return Files.isWritable(this.path) && !Files.isDirectory(this.path);
   }

   @Override
   public OutputStream getOutputStream() throws IOException {
      if (Files.isDirectory(this.path)) {
         throw new FileNotFoundException(this.getPath() + " (is a directory)");
      } else {
         return Files.newOutputStream(this.path);
      }
   }

   @Override
   public URL getURL() throws IOException {
      return this.path.toUri().toURL();
   }

   @Override
   public URI getURI() throws IOException {
      return this.path.toUri();
   }

   @Override
   public boolean isFile() {
      return true;
   }

   @Override
   public File getFile() {
      return this.path.toFile();
   }

   @Override
   public Path getFilePath() {
      return this.path;
   }

   @Override
   public ReadableByteChannel readableChannel() throws IOException {
      try {
         return Files.newByteChannel(this.path, StandardOpenOption.READ);
      } catch (NoSuchFileException ex) {
         throw new FileNotFoundException(ex.getMessage());
      }
   }

   @Override
   public WritableByteChannel writableChannel() throws IOException {
      return Files.newByteChannel(this.path, StandardOpenOption.WRITE);
   }

   @Override
   public long contentLength() throws IOException {
      return Files.size(this.path);
   }

   @Override
   public long lastModified() throws IOException {
      return Files.getLastModifiedTime(this.path).toMillis();
   }

   @Override
   public Resource createRelative(String relativePath) {
      return new PathResource(this.path.resolve(relativePath));
   }

   @Override
   public String getFilename() {
      return this.path.getFileName().toString();
   }

   @Override
   public String getDescription() {
      return "path [" + this.path.toAbsolutePath() + "]";
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof PathResource that && this.path.equals(that.path);
   }

   @Override
   public int hashCode() {
      return this.path.hashCode();
   }
}
