package org.springframework.boot.logging.log4j2;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.apache.logging.log4j.core.LogEvent;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.logging.StackTracePrinter;

class Extractor {
   private final @Nullable StackTracePrinter stackTracePrinter;

   Extractor(@Nullable StackTracePrinter stackTracePrinter) {
      this.stackTracePrinter = stackTracePrinter;
   }

   String messageAndStackTrace(LogEvent event) {
      return event.getMessage().getFormattedMessage() + "\n\n" + this.stackTrace(event);
   }

   @Nullable String stackTrace(LogEvent event) {
      return this.stackTrace(event.getThrown());
   }

   @Nullable String stackTrace(@Nullable Throwable throwable) {
      if (throwable == null) {
         return null;
      } else {
         return this.stackTracePrinter != null ? this.stackTracePrinter.printStackTraceToString(throwable) : printStackTrace(throwable);
      }
   }

   private static String printStackTrace(Throwable throwable) {
      StringWriter stringWriter = new StringWriter();
      throwable.printStackTrace(new PrintWriter(stringWriter));
      return stringWriter.toString();
   }
}
