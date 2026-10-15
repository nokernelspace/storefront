package org.springframework.core.task.support;

import io.micrometer.context.ContextSnapshotFactory;
import org.springframework.core.task.TaskDecorator;

public class ContextPropagatingTaskDecorator implements TaskDecorator {
   private final ContextSnapshotFactory factory;

   public ContextPropagatingTaskDecorator() {
      this(ContextSnapshotFactory.builder().build());
   }

   public ContextPropagatingTaskDecorator(ContextSnapshotFactory factory) {
      this.factory = factory;
   }

   @Override
   public Runnable decorate(Runnable runnable) {
      return this.factory.captureAll(new Object[0]).wrap(runnable);
   }
}
