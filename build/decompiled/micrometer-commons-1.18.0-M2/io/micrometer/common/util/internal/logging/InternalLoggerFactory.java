package io.micrometer.common.util.internal.logging;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

public abstract class InternalLoggerFactory {
   private static volatile @Nullable InternalLoggerFactory defaultFactory;

   private static InternalLoggerFactory newDefaultFactory(String name) {
      InternalLoggerFactory f;
      try {
         f = Slf4JLoggerFactory.INSTANCE;
         f.newInstance(name).debug("Using SLF4J as the default logging framework");
      } catch (Throwable ignored) {
         f = JdkLoggerFactory.INSTANCE;
         f.newInstance(name).debug("Using java.util.logging as the default logging framework");
      }

      return f;
   }

   public static InternalLoggerFactory getDefaultFactory() {
      if (defaultFactory == null) {
         defaultFactory = newDefaultFactory(InternalLoggerFactory.class.getName());
      }

      return defaultFactory;
   }

   public static void setDefaultFactory(InternalLoggerFactory defaultFactory) {
      Objects.requireNonNull(defaultFactory, "defaultFactory");
      InternalLoggerFactory.defaultFactory = defaultFactory;
   }

   public static InternalLogger getInstance(Class<?> clazz) {
      return getInstance(clazz.getName());
   }

   public static InternalLogger getInstance(String name) {
      return getDefaultFactory().newInstance(name);
   }

   protected abstract InternalLogger newInstance(String var1);
}
