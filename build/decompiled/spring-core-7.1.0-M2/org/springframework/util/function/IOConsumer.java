package org.springframework.util.function;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;

@FunctionalInterface
public interface IOConsumer<C> extends Consumer<C> {
   void acceptWithException(C content) throws IOException;

   @Override
   default void accept(C input) {
      try {
         this.acceptWithException(input);
      } catch (IOException ex) {
         throw new UncheckedIOException(ex);
      }
   }
}
