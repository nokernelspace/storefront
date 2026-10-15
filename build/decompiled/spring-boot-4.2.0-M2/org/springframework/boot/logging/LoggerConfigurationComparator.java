package org.springframework.boot.logging;

import java.util.Comparator;
import org.springframework.util.Assert;

class LoggerConfigurationComparator implements Comparator<LoggerConfiguration> {
   private final String rootLoggerName;

   LoggerConfigurationComparator(String rootLoggerName) {
      Assert.notNull(rootLoggerName, "'rootLoggerName' must not be null");
      this.rootLoggerName = rootLoggerName;
   }

   public int compare(LoggerConfiguration o1, LoggerConfiguration o2) {
      if (this.rootLoggerName.equals(o1.getName())) {
         return -1;
      } else {
         return this.rootLoggerName.equals(o2.getName()) ? 1 : o1.getName().compareTo(o2.getName());
      }
   }
}
