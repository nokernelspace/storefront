package org.springframework.aot.generate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.InputStreamSource;
import org.springframework.util.function.ThrowingConsumer;

class AppendableConsumerInputStreamSource implements InputStreamSource {
   private final ThrowingConsumer<Appendable> content;

   AppendableConsumerInputStreamSource(ThrowingConsumer<Appendable> content) {
      this.content = content;
   }

   @Override
   public InputStream getInputStream() {
      return new ByteArrayInputStream(this.toString().getBytes(StandardCharsets.UTF_8));
   }

   @Override
   public String toString() {
      StringBuilder buffer = new StringBuilder();
      this.content.accept(buffer);
      return buffer.toString();
   }
}
