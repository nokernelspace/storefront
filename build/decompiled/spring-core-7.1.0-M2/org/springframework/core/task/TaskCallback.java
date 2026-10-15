package org.springframework.core.task;

import java.util.concurrent.Callable;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface TaskCallback<V, E extends Exception> extends Callable<V> {
   @Override
   V call() throws E;

   static <V> TaskCallback<V, Exception> from(Callable<V> task) {
      return task::call;
   }

   static TaskCallback<@Nullable Void, RuntimeException> from(Runnable task) {
      return () -> {
         task.run();
         return null;
      };
   }
}
