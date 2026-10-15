package org.springframework.boot.logging.log4j2;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.util.ClassUtils;

class Log4J2RuntimeHints implements RuntimeHintsRegistrar {
   public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
      if (ClassUtils.isPresent("org.apache.logging.log4j.core.impl.Log4jContextFactory", classLoader)) {
         this.registerLog4j2Hints(hints, classLoader);
      }
   }

   private void registerLog4j2Hints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
      hints.reflection().registerTypeIfPresent(classLoader, "org.apache.logging.log4j.core.impl.Log4jContextFactory", new MemberCategory[0]);
      hints.resources().registerPattern("org/springframework/boot/logging/log4j2/log4j2.xml");
      hints.resources().registerPattern("org/springframework/boot/logging/log4j2/log4j2-file.xml");
      hints.resources().registerPattern("log4j2.springboot");
      hints.reflection().registerTypeIfPresent(classLoader, "org.apache.logging.log4j.jul.Log4jBridgeHandler", new MemberCategory[0]);
      hints.reflection().registerTypeIfPresent(classLoader, "org.apache.logging.log4j.jul.LogManager", new MemberCategory[0]);
   }
}
