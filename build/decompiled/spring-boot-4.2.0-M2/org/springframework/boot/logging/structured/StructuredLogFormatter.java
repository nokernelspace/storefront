package org.springframework.boot.logging.structured;

import java.nio.charset.Charset;

@FunctionalInterface
public interface StructuredLogFormatter<E> {
   String format(E event);

   default byte[] formatAsBytes(E event, Charset charset) {
      return this.format(event).getBytes(charset);
   }
}
