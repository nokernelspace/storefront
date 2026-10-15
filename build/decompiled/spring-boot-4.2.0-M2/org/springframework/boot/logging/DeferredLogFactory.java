package org.springframework.boot.logging;

import java.util.function.Supplier;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

@FunctionalInterface
public interface DeferredLogFactory {
   default Log getLog(Class<?> destination) {
      return this.getLog(() -> LogFactory.getLog(destination));
   }

   default Log getLog(Log destination) {
      return this.getLog(() -> destination);
   }

   Log getLog(Supplier<Log> destination);
}
