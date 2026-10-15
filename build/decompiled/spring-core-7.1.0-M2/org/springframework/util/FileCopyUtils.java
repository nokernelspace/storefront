package org.springframework.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import org.jspecify.annotations.Nullable;

public abstract class FileCopyUtils {
   public static final int BUFFER_SIZE = 8192;

   public static int copy(File in, File out) throws IOException {
      Assert.notNull(in, "No input File specified");
      Assert.notNull(out, "No output File specified");
      return copy(Files.newInputStream(in.toPath()), Files.newOutputStream(out.toPath()));
   }

   public static void copy(byte[] in, File out) throws IOException {
      Assert.notNull(in, "No input byte array specified");
      Assert.notNull(out, "No output File specified");
      Files.write(out.toPath(), in);
   }

   public static byte[] copyToByteArray(File in) throws IOException {
      Assert.notNull(in, "No input File specified");
      return Files.readAllBytes(in.toPath());
   }

   public static int copy(InputStream in, OutputStream out) throws IOException {
      Assert.notNull(in, "No InputStream specified");
      Assert.notNull(out, "No OutputStream specified");

      try (
         in;
         out;
      ) {
         int count = (int)in.transferTo(out);
         out.flush();
         return count;
      }
   }

   public static void copy(byte[] in, OutputStream out) throws IOException {
      Assert.notNull(in, "No input byte array specified");
      Assert.notNull(out, "No OutputStream specified");

      try (out) {
         out.write(in);
      }
   }

   public static byte[] copyToByteArray(@Nullable InputStream in) throws IOException {
      if (in == null) {
         return new byte[0];
      }

      try (in) {
         return in.readAllBytes();
      }
   }

   public static int copy(Reader in, Writer out) throws IOException {
      Assert.notNull(in, "No Reader specified");
      Assert.notNull(out, "No Writer specified");

      try (
         in;
         out;
      ) {
         int charCount = (int)in.transferTo(out);
         out.flush();
         return charCount;
      }
   }

   public static void copy(String in, Writer out) throws IOException {
      Assert.notNull(in, "No input String specified");
      Assert.notNull(out, "No Writer specified");

      try (out) {
         out.write(in);
      }
   }

   public static String copyToString(@Nullable Reader in) throws IOException {
      if (in == null) {
         return "";
      }

      StringWriter out = new StringWriter(8192);
      copy(in, out);
      return out.toString();
   }
}
