package org.springframework.boot.availability;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.util.Assert;

public class ApplicationAvailabilityBean implements ApplicationAvailability, ApplicationListener<AvailabilityChangeEvent<?>> {
   private final Map<Class<? extends AvailabilityState>, AvailabilityChangeEvent<?>> events = new ConcurrentHashMap<>();
   private final Log logger;

   public ApplicationAvailabilityBean() {
      this(LogFactory.getLog(ApplicationAvailabilityBean.class));
   }

   ApplicationAvailabilityBean(Log logger) {
      this.logger = logger;
   }

   @Override
   public <S extends AvailabilityState> S getState(Class<S> stateType, S defaultState) {
      Assert.notNull(stateType, "'stateType' must not be null");
      Assert.notNull(defaultState, "'defaultState' must not be null");
      S state = this.getState(stateType);
      return state != null ? state : defaultState;
   }

   @Override
   public <S extends AvailabilityState> @Nullable S getState(Class<S> stateType) {
      AvailabilityChangeEvent<S> event = this.getLastChangeEvent(stateType);
      return event != null ? event.getState() : null;
   }

   @Override
   public <S extends AvailabilityState> @Nullable AvailabilityChangeEvent<S> getLastChangeEvent(Class<S> stateType) {
      return (AvailabilityChangeEvent<S>)this.events.get(stateType);
   }

   public void onApplicationEvent(AvailabilityChangeEvent<?> event) {
      Class<? extends AvailabilityState> type = this.getStateType(event.getState());
      if (this.logger.isDebugEnabled()) {
         this.logger.debug(this.getLogMessage(type, event));
      }

      this.events.put(type, event);
   }

   private <S extends AvailabilityState> Object getLogMessage(Class<S> type, AvailabilityChangeEvent<?> event) {
      AvailabilityChangeEvent<S> lastChangeEvent = this.getLastChangeEvent(type);
      StringBuilder message = new StringBuilder("Application availability state " + type.getSimpleName() + " changed");
      message.append(lastChangeEvent != null ? " from " + lastChangeEvent.getState() : "");
      message.append(" to " + event.getState());
      message.append(this.getSourceDescription(event.getSource()));
      return message;
   }

   private String getSourceDescription(@Nullable Object source) {
      return source != null && !(source instanceof ApplicationEventPublisher)
         ? ": " + (source instanceof Throwable ? source : source.getClass().getName())
         : "";
   }

   private Class<? extends AvailabilityState> getStateType(AvailabilityState state) {
      return (Class<? extends AvailabilityState>)(state instanceof Enum<?> enumState ? enumState.getDeclaringClass() : state.getClass());
   }
}
