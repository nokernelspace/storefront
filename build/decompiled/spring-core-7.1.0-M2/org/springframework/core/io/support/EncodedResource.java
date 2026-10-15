package org.springframework.core.io.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.function.IOConsumer;

public class EncodedResource implements InputStreamSource {
   private final Resource resource;
   private final @Nullable String encoding;
   private final @Nullable Charset charset;

   public EncodedResource(Resource resource) {
      this(resource, null, null);
   }

   public EncodedResource(Resource resource, @Nullable String encoding) {
      this(resource, encoding, null);
   }

   public EncodedResource(Resource resource, @Nullable Charset charset) {
      this(resource, null, charset);
   }

   private EncodedResource(Resource resource, @Nullable String encoding, @Nullable Charset charset) {
      Assert.notNull(resource, "Resource must not be null");
      this.resource = resource;
      this.encoding = encoding;
      this.charset = charset;
   }

   public final Resource getResource() {
      return this.resource;
   }

   public final @Nullable String getEncoding() {
      return this.encoding;
   }

   public final @Nullable Charset getCharset() {
      return this.charset;
   }

   public boolean requiresReader() {
      return this.encoding != null || this.charset != null;
   }

   @Override
   public InputStream getInputStream() throws IOException {
      return this.resource.getInputStream();
   }

   public Reader getReader() throws IOException {
      return this.getReader(this.resource.getInputStream());
   }

   private Reader getReader(InputStream inputStream) throws IOException {
      if (this.charset != null) {
         return new InputStreamReader(inputStream, this.charset);
      } else {
         return this.encoding != null ? new InputStreamReader(inputStream, this.encoding) : new InputStreamReader(inputStream);
      }
   }

   public void consumeContent(IOConsumer<Reader> consumer) throws IOException {
      this.resource.consumeContent(inputStream -> consumer.accept(this.getReader(inputStream)));
   }

   public String getContentAsString() throws IOException {
      Charset charset;
      if (this.charset != null) {
         charset = this.charset;
      } else if (this.encoding != null) {
         charset = Charset.forName(this.encoding);
      } else {
         charset = Charset.defaultCharset();
      }

      return this.resource.getContentAsString(charset);
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof EncodedResource that
            && this.resource.equals(that.resource)
            && ObjectUtils.nullSafeEquals(this.charset, that.charset)
            && ObjectUtils.nullSafeEquals(this.encoding, that.encoding);
   }

   @Override
   public int hashCode() {
      return this.resource.hashCode();
   }

   @Override
   public String toString() {
      return this.resource.toString();
   }
}
