package org.springframework.core.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.function.Supplier;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ResourceUtils;

public abstract class AbstractResource implements Resource {
   @Override
   public boolean exists() {
      if (this.isFile()) {
         try {
            return this.getFile().exists();
         } catch (IOException ex) {
            this.debug(() -> "Could not retrieve File for existence check of " + this.getDescription(), ex);
         }
      }

      try {
         this.getInputStream().close();
         return true;
      } catch (Throwable ex) {
         this.debug(() -> "Could not retrieve InputStream for existence check of " + this.getDescription(), ex);
         return false;
      }
   }

   @Override
   public boolean isReadable() {
      return this.exists();
   }

   @Override
   public boolean isOpen() {
      return false;
   }

   @Override
   public boolean isFile() {
      return false;
   }

   @Override
   public URL getURL() throws IOException {
      throw new FileNotFoundException(this.getDescription() + " cannot be resolved to URL");
   }

   @Override
   public URI getURI() throws IOException {
      URL url = this.getURL();

      try {
         return ResourceUtils.toURI(url);
      } catch (URISyntaxException ex) {
         throw new IOException("Invalid URI [" + url + "]", ex);
      }
   }

   @Override
   public File getFile() throws IOException {
      throw new FileNotFoundException(this.getDescription() + " cannot be resolved to absolute file path");
   }

   @Override
   public ReadableByteChannel readableChannel() throws IOException {
      return Channels.newChannel(this.getInputStream());
   }

   @Override
   public long contentLength() throws IOException {
      InputStream is = this.getInputStream();

      try {
         long size = 0L;
         byte[] buf = new byte[256];

         int read;
         while ((read = is.read(buf)) != -1) {
            size += read;
         }

         return size;
      } finally {
         try {
            is.close();
         } catch (IOException ex) {
            this.debug(() -> "Could not close content-length InputStream for " + this.getDescription(), ex);
         }
      }
   }

   @Override
   public long lastModified() throws IOException {
      File fileToCheck = this.getFileForLastModifiedCheck();
      long lastModified = fileToCheck.lastModified();
      if (lastModified == 0L && !fileToCheck.exists()) {
         throw new FileNotFoundException(this.getDescription() + " cannot be resolved in the file system for checking its last-modified timestamp");
      } else {
         return lastModified;
      }
   }

   protected File getFileForLastModifiedCheck() throws IOException {
      return this.getFile();
   }

   @Override
   public Resource createRelative(String relativePath) throws IOException {
      throw new FileNotFoundException("Cannot create a relative resource for " + this.getDescription());
   }

   @Override
   public @Nullable String getFilename() {
      return null;
   }

   private void debug(Supplier<String> message, Throwable ex) {
      Log logger = LogFactory.getLog(this.getClass());
      if (logger.isDebugEnabled()) {
         logger.debug(message.get(), ex);
      }
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof Resource that && this.getDescription().equals(that.getDescription());
   }

   @Override
   public int hashCode() {
      return this.getDescription().hashCode();
   }

   @Override
   public String toString() {
      return this.getDescription();
   }
}
