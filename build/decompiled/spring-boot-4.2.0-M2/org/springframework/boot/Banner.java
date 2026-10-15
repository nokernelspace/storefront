package org.springframework.boot;

import java.io.PrintStream;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.Environment;

@FunctionalInterface
public interface Banner {
   void printBanner(Environment environment, @Nullable Class<?> sourceClass, PrintStream out);

   enum Mode {
      OFF,
      CONSOLE,
      LOG;
   }
}
