package io.micrometer.common.util.internal.logging;

import java.util.logging.Logger;

public class JdkLoggerFactory extends InternalLoggerFactory {
   public static final InternalLoggerFactory INSTANCE = new JdkLoggerFactory();

   private JdkLoggerFactory() {
   }

   @Override
   public InternalLogger newInstance(String name) {
      return new JdkLogger(Logger.getLogger(name));
   }
}
