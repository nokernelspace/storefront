package org.springframework.core.metrics;

import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public interface StartupStep extends AutoCloseable {
   String getName();

   long getId();

   @Nullable Long getParentId();

   StartupStep tag(String key, String value);

   StartupStep tag(String key, Supplier<String> value);

   StartupStep.Tags getTags();

   void end();

   @Override
   default void close() {
      this.end();
   }

   interface Tag {
      String getKey();

      String getValue();
   }

   interface Tags extends Iterable<StartupStep.Tag> {
   }
}
