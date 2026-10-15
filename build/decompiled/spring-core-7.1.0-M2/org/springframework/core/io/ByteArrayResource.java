package org.springframework.core.io;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public class ByteArrayResource extends AbstractResource {
   private final byte[] byteArray;
   private final String description;

   public ByteArrayResource(byte[] byteArray) {
      this(byteArray, "resource loaded from byte array");
   }

   public ByteArrayResource(byte[] byteArray, @Nullable String description) {
      Assert.notNull(byteArray, "Byte array must not be null");
      this.byteArray = byteArray;
      this.description = description != null ? description : "";
   }

   public final byte[] getByteArray() {
      return this.byteArray;
   }

   @Override
   public boolean exists() {
      return true;
   }

   @Override
   public long contentLength() {
      return this.byteArray.length;
   }

   @Override
   public InputStream getInputStream() throws IOException {
      return new ByteArrayInputStream(this.byteArray);
   }

   @Override
   public byte[] getContentAsByteArray() throws IOException {
      int length = this.byteArray.length;
      byte[] result = new byte[length];
      System.arraycopy(this.byteArray, 0, result, 0, length);
      return result;
   }

   @Override
   public String getContentAsString(Charset charset) throws IOException {
      return new String(this.byteArray, charset);
   }

   @Override
   public String getDescription() {
      return "Byte array resource [" + this.description + "]";
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof ByteArrayResource that && Arrays.equals(this.byteArray, that.byteArray);
   }

   @Override
   public int hashCode() {
      return Arrays.hashCode(this.byteArray);
   }
}
