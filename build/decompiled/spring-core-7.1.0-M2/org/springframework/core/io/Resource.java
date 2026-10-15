package org.springframework.core.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.function.IOConsumer;

public interface Resource extends InputStreamSource {
   boolean exists();

   default boolean isReadable() {
      return this.exists();
   }

   default boolean isOpen() {
      return false;
   }

   default boolean isFile() {
      return false;
   }

   URL getURL() throws IOException;

   URI getURI() throws IOException;

   File getFile() throws IOException;

   default Path getFilePath() throws IOException {
      return this.getFile().toPath();
   }

   default ReadableByteChannel readableChannel() throws IOException {
      return Channels.newChannel(this.getInputStream());
   }

   default void consumeContent(IOConsumer<InputStream> consumer) throws IOException {
      try (InputStream inputStream = this.getInputStream()) {
         consumer.accept(inputStream);
      }
   }

   default byte[] getContentAsByteArray() throws IOException {
      return FileCopyUtils.copyToByteArray(this.getInputStream());
   }

   default String getContentAsString(Charset charset) throws IOException {
      return FileCopyUtils.copyToString(new InputStreamReader(this.getInputStream(), charset));
   }

   long contentLength() throws IOException;

   long lastModified() throws IOException;

   Resource createRelative(String relativePath) throws IOException;

   @Nullable String getFilename();

   String getDescription();
}
