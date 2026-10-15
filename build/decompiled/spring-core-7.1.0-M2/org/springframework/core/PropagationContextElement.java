package org.springframework.core;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.context.ContextSnapshot.Scope;
import kotlin.coroutines.AbstractCoroutineContextElement;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.ThreadContextElement;
import kotlinx.coroutines.reactor.ReactorContext;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ClassUtils;
import reactor.util.context.ContextView;

public final class PropagationContextElement extends AbstractCoroutineContextElement implements ThreadContextElement<Scope> {
   public static final PropagationContextElement.Key Key = new PropagationContextElement.Key();
   private static final ContextSnapshotFactory contextSnapshotFactory = ContextSnapshotFactory.builder().contextRegistry(ContextRegistry.getInstance()).build();
   private static final boolean coroutinesReactorPresent = ClassUtils.isPresent(
      "kotlinx.coroutines.reactor.ReactorContext", PropagationContextElement.class.getClassLoader()
   );
   private final ContextSnapshot threadLocalContextSnapshot = contextSnapshotFactory.captureAll(new Object[0]);

   public PropagationContextElement() {
      super(Key);
   }

   public void restoreThreadContext(CoroutineContext context, Scope oldState) {
      oldState.close();
   }

   public Scope updateThreadContext(CoroutineContext context) {
      ContextSnapshot contextSnapshot;
      if (coroutinesReactorPresent) {
         contextSnapshot = PropagationContextElement.ReactorDelegate.captureFrom(context);
         if (contextSnapshot == null) {
            contextSnapshot = this.threadLocalContextSnapshot;
         }
      } else {
         contextSnapshot = this.threadLocalContextSnapshot;
      }

      return contextSnapshot.setThreadLocals();
   }

   public static final class Key implements kotlin.coroutines.CoroutineContext.Key<PropagationContextElement> {
   }

   private static final class ReactorDelegate {
      public static @Nullable ContextSnapshot captureFrom(CoroutineContext context) {
         ReactorContext reactorContext = (ReactorContext)context.get(ReactorContext.Key);
         ContextView contextView = reactorContext != null ? reactorContext.getContext() : null;
         return contextView != null ? PropagationContextElement.contextSnapshotFactory.captureFrom(new Object[]{contextView}) : null;
      }
   }
}
