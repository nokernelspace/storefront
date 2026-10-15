package org.springframework.util;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import org.jspecify.annotations.Nullable;

public abstract class StreamUtils {
   public static final int BUFFER_SIZE = 8192;
   private static final byte[] EMPTY_CONTENT = new byte[0];

   public static byte[] copyToByteArray(@Nullable InputStream in) throws IOException {
      return in == null ? EMPTY_CONTENT : in.readAllBytes();
   }

   public static String copyToString(@Nullable InputStream in, Charset charset) throws IOException {
      if (in == null) {
         return "";
      }

      StringBuilder out = new StringBuilder();
      InputStreamReader reader = new InputStreamReader(in, charset);
      char[] buffer = new char[8192];

      int charsRead;
      while ((charsRead = reader.read(buffer)) != -1) {
         out.append(buffer, 0, charsRead);
      }

      return out.toString();
   }

   public static String copyToString(ByteArrayOutputStream baos, Charset charset) {
      Assert.notNull(baos, "No ByteArrayOutputStream specified");
      Assert.notNull(charset, "No Charset specified");
      return baos.toString(charset);
   }

   public static void copy(byte[] in, OutputStream out) throws IOException {
      Assert.notNull(in, "No input byte array specified");
      Assert.notNull(out, "No OutputStream specified");
      out.write(in);
      out.flush();
   }

   public static void copy(String in, Charset charset, OutputStream out) throws IOException {
      Assert.notNull(in, "No input String specified");
      Assert.notNull(charset, "No Charset specified");
      Assert.notNull(out, "No OutputStream specified");
      out.write(in.getBytes(charset));
      out.flush();
   }

   public static int copy(InputStream in, OutputStream out) throws IOException {
      Assert.notNull(in, "No InputStream specified");
      Assert.notNull(out, "No OutputStream specified");
      int count = (int)in.transferTo(out);
      out.flush();
      return count;
   }

   public static long copyRange(InputStream in, OutputStream out, long start, long end) throws IOException {
      Assert.notNull(in, "No InputStream specified");
      Assert.notNull(out, "No OutputStream specified");
      long skipped = in.skip(start);
      if (skipped < start) {
         throw new IOException("Skipped only " + skipped + " bytes out of " + start + " required");
      }

      long bytesToCopy = end - start + 1L;
      byte[] buffer = new byte[(int)Math.min(8192L, bytesToCopy)];

      while (bytesToCopy > 0L) {
         int bytesRead = bytesToCopy < buffer.length ? in.read(buffer, 0, (int)bytesToCopy) : in.read(buffer);
         if (bytesRead == -1) {
            break;
         }

         out.write(buffer, 0, bytesRead);
         bytesToCopy -= bytesRead;
      }

      return end - start + 1L - bytesToCopy;
   }

   public static int drain(@Nullable InputStream in) throws IOException {
      return in == null ? 0 : (int)in.transferTo(OutputStream.nullOutputStream());
   }

   @Deprecated(since = "6.0")
   public static InputStream emptyInput() {
      return InputStream.nullInputStream();
   }

   public static InputStream nonClosing(InputStream in) {
      if (in instanceof StreamUtils.NonClosingInputStream) {
         return in;
      }

      Assert.notNull(in, "No InputStream specified");
      return new StreamUtils.NonClosingInputStream(in);
   }

   public static OutputStream nonClosing(OutputStream out) {
      if (out instanceof StreamUtils.NonClosingOutputStream) {
         return out;
      }

      Assert.notNull(out, "No OutputStream specified");
      return new StreamUtils.NonClosingOutputStream(out);
   }

   public static OutputStream nonFlushing(OutputStream out) {
      if (out instanceof StreamUtils.NonFlushingOutputStream) {
         return out;
      }

      Assert.notNull(out, "No OutputStream specified");
      return new StreamUtils.NonFlushingOutputStream(out);
   }

   private static class NonClosingInputStream extends FilterInputStream {
      public NonClosingInputStream(InputStream in) {
         super(in);
      }

      @Override
      public void close() throws IOException {
      }

      @Override
      public byte[] readAllBytes() throws IOException {
         return this.in.readAllBytes();
      }

      @Override
      public byte[] readNBytes(int len) throws IOException {
         return this.in.readNBytes(len);
      }

      @Override
      public int readNBytes(byte[] b, int off, int len) throws IOException {
         return this.in.readNBytes(b, off, len);
      }

      @Override
      public long transferTo(OutputStream out) throws IOException {
         return this.in.transferTo(out);
      }
   }

   private static class NonClosingOutputStream extends FilterOutputStream {
      public NonClosingOutputStream(OutputStream out) {
         super(out);
      }

      @Override
      public void write(byte[] b, int off, int let) throws IOException {
         this.out.write(b, off, let);
      }

      @Override
      public void close() throws IOException {
      }
   }

   private static class NonFlushingOutputStream extends StreamUtils.NonClosingOutputStream {
      public NonFlushingOutputStream(OutputStream out) {
         super(out);
      }

      @Override
      public void flush() throws IOException {
      }
   }
}
