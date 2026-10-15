package org.springframework.core.metrics;

import java.util.Collections;
import java.util.Iterator;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

class DefaultApplicationStartup implements ApplicationStartup {
   private static final DefaultApplicationStartup.DefaultStartupStep DEFAULT_STARTUP_STEP = new DefaultApplicationStartup.DefaultStartupStep();

   public DefaultApplicationStartup.DefaultStartupStep start(String name) {
      return DEFAULT_STARTUP_STEP;
   }

   static class DefaultStartupStep implements StartupStep {
      private final DefaultApplicationStartup.DefaultStartupStep.DefaultTags TAGS = new DefaultApplicationStartup.DefaultStartupStep.DefaultTags();

      @Override
      public String getName() {
         return "default";
      }

      @Override
      public long getId() {
         return 0L;
      }

      @Override
      public @Nullable Long getParentId() {
         return null;
      }

      @Override
      public StartupStep.Tags getTags() {
         return this.TAGS;
      }

      @Override
      public StartupStep tag(String key, String value) {
         return this;
      }

      @Override
      public StartupStep tag(String key, Supplier<String> value) {
         return this;
      }

      @Override
      public void end() {
      }

      static class DefaultTags implements StartupStep.Tags {
         @Override
         public Iterator<StartupStep.Tag> iterator() {
            return Collections.emptyIterator();
         }
      }
   }
}
