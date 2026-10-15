package org.springframework.boot.logging;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class DeferredLogs implements DeferredLogFactory {
   private final DeferredLog.Lines lines = new DeferredLog.Lines();
   private final List<DeferredLog> loggers = new ArrayList<>();

   @Override
   public Log getLog(Class<?> destination) {
      return this.getLog(() -> LogFactory.getLog(destination));
   }

   @Override
   public Log getLog(Log destination) {
      return this.getLog(() -> destination);
   }

   @Override
   public Log getLog(Supplier<Log> destination) {
      synchronized (this.lines) {
         DeferredLog logger = new DeferredLog(destination, this.lines);
         this.loggers.add(logger);
         return logger;
      }
   }

   public void switchOverAll() {
      synchronized (this.lines) {
         for (DeferredLog.Line line : this.lines) {
            line.getLevel().log(line.getDestination(), line.getMessage(), line.getThrowable());
         }

         for (DeferredLog logger : this.loggers) {
            logger.switchOver();
         }

         this.lines.clear();
      }
   }
}
