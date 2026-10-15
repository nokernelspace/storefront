package org.springframework.boot.thread;

import org.springframework.boot.system.JavaVersion;
import org.springframework.core.env.Environment;

public enum Threading {
   PLATFORM {
      @Override
      public boolean isActive(Environment environment) {
         return !VIRTUAL.isActive(environment);
      }
   },
   VIRTUAL {
      @Override
      public boolean isActive(Environment environment) {
         return (Boolean)environment.getProperty("spring.threads.virtual.enabled", boolean.class, false)
            && JavaVersion.getJavaVersion().isEqualOrNewerThan(JavaVersion.TWENTY_ONE);
      }
   };

   public abstract boolean isActive(Environment environment);
}
