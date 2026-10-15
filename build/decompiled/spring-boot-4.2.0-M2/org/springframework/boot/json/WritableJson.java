package org.springframework.boot.json;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.WritableResource;
import org.springframework.util.Assert;

@FunctionalInterface
public interface WritableJson {
   void to(Appendable out) throws IOException;

   default String toJsonString() {
      try {
         StringBuilder stringBuilder = new StringBuilder();
         this.to(stringBuilder);
         return stringBuilder.toString();
      } catch (IOException ex) {
         throw new UncheckedIOException(ex);
      }
   }

   default byte[] toByteArray() {
      return this.toByteArray(StandardCharsets.UTF_8);
   }

   default byte[] toByteArray(Charset charset) {
      Assert.notNull(charset, "'charset' must not be null");

      try {
         return AppendableByteArray.toByteArray(charset, this::to);
      } catch (IOException ex) {
         throw new UncheckedIOException(ex);
      }
   }

   default void toResource(WritableResource out) throws IOException {
      Assert.notNull(out, "'out' must not be null");

      try (OutputStream outputStream = out.getOutputStream()) {
         this.toOutputStream(outputStream);
      }
   }

   default void toResource(WritableResource out, Charset charset) throws IOException {
      Assert.notNull(out, "'out' must not be null");
      Assert.notNull(charset, "'charset' must not be null");

      try (OutputStream outputStream = out.getOutputStream()) {
         this.toOutputStream(outputStream, charset);
      }
   }

   default void toOutputStream(OutputStream out) throws IOException {
      this.toOutputStream(out, StandardCharsets.UTF_8);
   }

   default void toOutputStream(OutputStream out, Charset charset) throws IOException {
      Assert.notNull(out, "'out' must not be null");
      Assert.notNull(charset, "'charset' must not be null");
      this.toWriter(new OutputStreamWriter(out, charset));
   }

   default void toWriter(Writer out) throws IOException {
      Assert.notNull(out, "'out' must not be null");
      this.to(out);
      out.flush();
   }

   static WritableJson of(WritableJson writableJson) {
      return new WritableJson() {
         @Override
         public void to(Appendable out) throws IOException {
            writableJson.to(out);
         }

         @Override
         public String toString() {
            return this.toJsonString();
         }
      };
   }
}
