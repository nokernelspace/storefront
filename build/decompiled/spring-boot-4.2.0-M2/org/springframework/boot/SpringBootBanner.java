package org.springframework.boot;

import java.io.PrintStream;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.boot.ansi.AnsiStyle;
import org.springframework.core.env.Environment;

class SpringBootBanner implements Banner {
   private static final String BANNER = "  .   ____          _            __ _ _\n /\\\\ / ___'_ __ _ _(_)_ __  __ _ \\ \\ \\ \\\n( ( )\\___ | '_ | '_| | '_ \\/ _` | \\ \\ \\ \\\n \\\\/  ___)| |_)| | | | | || (_| |  ) ) ) )\n  '  |____| .__|_| |_|_| |_\\__, | / / / /\n =========|_|==============|___/=/_/_/_/\n";
   private static final String SPRING_BOOT = " :: Spring Boot :: ";
   private static final int STRAP_LINE_SIZE = 42;

   @Override
   public void printBanner(Environment environment, @Nullable Class<?> sourceClass, PrintStream printStream) {
      printStream.println();
      printStream.println(
         "  .   ____          _            __ _ _\n /\\\\ / ___'_ __ _ _(_)_ __  __ _ \\ \\ \\ \\\n( ( )\\___ | '_ | '_| | '_ \\/ _` | \\ \\ \\ \\\n \\\\/  ___)| |_)| | | | | || (_| |  ) ) ) )\n  '  |____| .__|_| |_|_| |_\\__, | / / / /\n =========|_|==============|___/=/_/_/_/\n"
      );
      String version = String.format(" (v%s)", SpringBootVersion.getVersion());
      String padding = " ".repeat(Math.max(0, 42 - (version.length() + " :: Spring Boot :: ".length())));
      printStream.println(AnsiOutput.toString(AnsiColor.GREEN, " :: Spring Boot :: ", AnsiColor.DEFAULT, padding, AnsiStyle.FAINT, version));
      printStream.println();
   }
}
