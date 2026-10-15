package org.springframework.boot.context.config;

import org.apache.commons.logging.Log;
import org.springframework.core.log.LogMessage;

public enum ConfigDataNotFoundAction {
   FAIL {
      @Override
      void handle(Log logger, ConfigDataNotFoundException ex) {
         throw ex;
      }
   },
   IGNORE {
      @Override
      void handle(Log logger, ConfigDataNotFoundException ex) {
         logger.trace(LogMessage.format("Ignoring missing config data %s", ex.getReferenceDescription()));
      }
   };

   abstract void handle(Log logger, ConfigDataNotFoundException ex);
}
