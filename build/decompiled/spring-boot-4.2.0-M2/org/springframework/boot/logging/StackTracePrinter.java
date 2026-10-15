package org.springframework.boot.logging;

import java.io.IOException;
import java.io.UncheckedIOException;

@FunctionalInterface
public interface StackTracePrinter {
   default String printStackTraceToString(Throwable throwable) {
      try {
         StringBuilder out = new StringBuilder(4096);
         this.printStackTrace(throwable, out);
         return out.toString();
      } catch (IOException ex) {
         throw new UncheckedIOException(ex);
      }
   }

   void printStackTrace(Throwable throwable, Appendable out) throws IOException;
}
