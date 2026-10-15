package org.springframework.boot.logging.logback;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.pattern.SyslogStartConverter;
import ch.qos.logback.core.rolling.helper.DateTokenConverter;
import ch.qos.logback.core.rolling.helper.IntegerTokenConverter;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.util.ClassUtils;

class LogbackRuntimeHints implements RuntimeHintsRegistrar {
   public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
      if (ClassUtils.isPresent("ch.qos.logback.classic.LoggerContext", classLoader)) {
         ReflectionHints reflection = hints.reflection();
         this.registerHintsForLogbackLoggingSystemTypeChecks(reflection, classLoader);
         this.registerHintsForBuiltInLogbackConverters(reflection);
         this.registerHintsForSpringBootConverters(reflection);
      }
   }

   private void registerHintsForLogbackLoggingSystemTypeChecks(ReflectionHints reflection, @Nullable ClassLoader classLoader) {
      reflection.registerType(LoggerContext.class, new MemberCategory[0]);
      reflection.registerTypeIfPresent(classLoader, "org.slf4j.bridge.SLF4JBridgeHandler", typeHint -> {});
   }

   private void registerHintsForBuiltInLogbackConverters(ReflectionHints reflection) {
      this.registerForPublicConstructorInvocation(reflection, DateTokenConverter.class, IntegerTokenConverter.class, SyslogStartConverter.class);
   }

   private void registerHintsForSpringBootConverters(ReflectionHints reflection) {
      this.registerForPublicConstructorInvocation(
         reflection,
         ColorConverter.class,
         EnclosedInSquareBracketsConverter.class,
         ExtendedWhitespaceThrowableProxyConverter.class,
         WhitespaceThrowableProxyConverter.class,
         CorrelationIdConverter.class
      );
   }

   private void registerForPublicConstructorInvocation(ReflectionHints reflection, Class<?>... classes) {
      reflection.registerTypes(TypeReference.listOf(classes), hint -> hint.withMembers(new MemberCategory[]{MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS}));
   }
}
