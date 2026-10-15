package org.springframework.boot.logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.classic.spi.Configurator.ExecutionStatus;
import ch.qos.logback.core.spi.ContextAwareBase;

public class RootLogLevelConfigurator extends ContextAwareBase implements Configurator {
   public ExecutionStatus configure(LoggerContext loggerContext) {
      loggerContext.getLogger("ROOT").setLevel(Level.INFO);
      return ExecutionStatus.INVOKE_NEXT_IF_ANY;
   }
}
