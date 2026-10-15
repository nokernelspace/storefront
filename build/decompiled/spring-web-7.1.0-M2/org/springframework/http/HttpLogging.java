package org.springframework.http;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.log.LogDelegateFactory;

public abstract class HttpLogging {
   private static final Log fallbackLogger = LogFactory.getLog("org.springframework.web." + HttpLogging.class.getSimpleName());

   public static Log forLogName(Class<?> primaryLoggerClass) {
      Log primaryLogger = LogFactory.getLog(primaryLoggerClass);
      return forLog(primaryLogger);
   }

   public static Log forLog(Log primaryLogger) {
      return LogDelegateFactory.getCompositeLog(primaryLogger, fallbackLogger, new Log[0]);
   }
}
