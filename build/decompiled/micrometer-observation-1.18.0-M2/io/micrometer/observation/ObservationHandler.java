package io.micrometer.observation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

public interface ObservationHandler<T extends Observation.Context> {
   default void onStart(T context) {
   }

   default void onError(T context) {
   }

   default void onEvent(Observation.Event event, T context) {
   }

   default void onScopeOpened(T context) {
   }

   default void onScopeClosed(T context) {
   }

   @Deprecated
   default void onScopeReset(T context) {
   }

   default void onStop(T context) {
   }

   boolean supportsContext(Observation.Context var1);

   class AllMatchingCompositeObservationHandler implements ObservationHandler.CompositeObservationHandler {
      private final List<ObservationHandler<Observation.Context>> handlers;

      @SafeVarargs
      public AllMatchingCompositeObservationHandler(ObservationHandler<? extends Observation.Context>... handlers) {
         this(Arrays.asList(handlers));
      }

      public AllMatchingCompositeObservationHandler(List<? extends ObservationHandler<? extends Observation.Context>> handlers) {
         List<ObservationHandler<Observation.Context>> castedHandlers = new ArrayList<>(handlers.size());

         for (ObservationHandler<? extends Observation.Context> handler : handlers) {
            castedHandlers.add(handler);
         }

         this.handlers = castedHandlers;
      }

      @Override
      public List<ObservationHandler<Observation.Context>> getHandlers() {
         return this.handlers;
      }

      @Override
      public void onStart(Observation.Context context) {
         for (ObservationHandler<Observation.Context> handler : this.handlers) {
            if (handler.supportsContext(context)) {
               handler.onStart(context);
            }
         }
      }

      @Override
      public void onError(Observation.Context context) {
         for (ObservationHandler<Observation.Context> handler : this.handlers) {
            if (handler.supportsContext(context)) {
               handler.onError(context);
            }
         }
      }

      @Override
      public void onEvent(Observation.Event event, Observation.Context context) {
         for (ObservationHandler<Observation.Context> handler : this.handlers) {
            if (handler.supportsContext(context)) {
               handler.onEvent(event, context);
            }
         }
      }

      @Override
      public void onScopeOpened(Observation.Context context) {
         for (ObservationHandler<Observation.Context> handler : this.handlers) {
            if (handler.supportsContext(context)) {
               handler.onScopeOpened(context);
            }
         }
      }

      @Override
      public void onScopeClosed(Observation.Context context) {
         for (ObservationHandler<Observation.Context> handler : this.handlers) {
            if (handler.supportsContext(context)) {
               handler.onScopeClosed(context);
            }
         }
      }

      @Deprecated
      @Override
      public void onScopeReset(Observation.Context context) {
         for (ObservationHandler<Observation.Context> handler : this.handlers) {
            if (handler.supportsContext(context)) {
               handler.onScopeReset(context);
            }
         }
      }

      @Override
      public void onStop(Observation.Context context) {
         for (ObservationHandler<Observation.Context> handler : this.handlers) {
            if (handler.supportsContext(context)) {
               handler.onStop(context);
            }
         }
      }

      @Override
      public boolean supportsContext(Observation.Context context) {
         for (ObservationHandler<Observation.Context> handler : this.handlers) {
            if (handler.supportsContext(context)) {
               return true;
            }
         }

         return false;
      }
   }

   interface CompositeObservationHandler extends ObservationHandler<Observation.Context> {
      List<ObservationHandler<Observation.Context>> getHandlers();
   }

   class FirstMatchingCompositeObservationHandler implements ObservationHandler.CompositeObservationHandler {
      private final List<ObservationHandler<Observation.Context>> handlers;

      @SafeVarargs
      public FirstMatchingCompositeObservationHandler(ObservationHandler<? extends Observation.Context>... handlers) {
         this(Arrays.asList(handlers));
      }

      public FirstMatchingCompositeObservationHandler(List<? extends ObservationHandler<? extends Observation.Context>> handlers) {
         List<ObservationHandler<Observation.Context>> castedHandlers = new ArrayList<>(handlers.size());

         for (ObservationHandler<? extends Observation.Context> handler : handlers) {
            castedHandlers.add(handler);
         }

         this.handlers = castedHandlers;
      }

      @Override
      public List<ObservationHandler<Observation.Context>> getHandlers() {
         return this.handlers;
      }

      @Override
      public void onStart(Observation.Context context) {
         ObservationHandler<Observation.Context> handler = this.getFirstApplicableHandler(context);
         if (handler != null) {
            handler.onStart(context);
         }
      }

      @Override
      public void onError(Observation.Context context) {
         ObservationHandler<Observation.Context> handler = this.getFirstApplicableHandler(context);
         if (handler != null) {
            handler.onError(context);
         }
      }

      @Override
      public void onEvent(Observation.Event event, Observation.Context context) {
         ObservationHandler<Observation.Context> handler = this.getFirstApplicableHandler(context);
         if (handler != null) {
            handler.onEvent(event, context);
         }
      }

      @Override
      public void onScopeOpened(Observation.Context context) {
         ObservationHandler<Observation.Context> handler = this.getFirstApplicableHandler(context);
         if (handler != null) {
            handler.onScopeOpened(context);
         }
      }

      @Override
      public void onScopeClosed(Observation.Context context) {
         ObservationHandler<Observation.Context> handler = this.getFirstApplicableHandler(context);
         if (handler != null) {
            handler.onScopeClosed(context);
         }
      }

      @Deprecated
      @Override
      public void onScopeReset(Observation.Context context) {
         ObservationHandler<Observation.Context> handler = this.getFirstApplicableHandler(context);
         if (handler != null) {
            handler.onScopeReset(context);
         }
      }

      @Override
      public void onStop(Observation.Context context) {
         ObservationHandler<Observation.Context> handler = this.getFirstApplicableHandler(context);
         if (handler != null) {
            handler.onStop(context);
         }
      }

      @Override
      public boolean supportsContext(Observation.Context context) {
         ObservationHandler<Observation.Context> handler = this.getFirstApplicableHandler(context);
         return handler != null;
      }

      private @Nullable ObservationHandler<Observation.Context> getFirstApplicableHandler(Observation.Context context) {
         for (ObservationHandler<Observation.Context> handler : this.handlers) {
            if (handler.supportsContext(context)) {
               return handler;
            }
         }

         return null;
      }
   }
}
