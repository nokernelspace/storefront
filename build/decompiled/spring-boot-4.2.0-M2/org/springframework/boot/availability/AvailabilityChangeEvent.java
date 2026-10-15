package org.springframework.boot.availability;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.core.ResolvableType;
import org.springframework.util.Assert;

public class AvailabilityChangeEvent<S extends AvailabilityState> extends PayloadApplicationEvent<S> {
   public AvailabilityChangeEvent(Object source, S state) {
      super(source, state);
   }

   public S getState() {
      return (S)this.getPayload();
   }

   public ResolvableType getResolvableType() {
      return ResolvableType.forClassWithGenerics(this.getClass(), new Class[]{this.getStateType()});
   }

   private Class<?> getStateType() {
      S state = this.getState();
      return state instanceof Enum<?> enumState ? enumState.getDeclaringClass() : state.getClass();
   }

   public static <S extends AvailabilityState> void publish(ApplicationContext context, S state) {
      Assert.notNull(context, "'context' must not be null");
      publish(context, context, state);
   }

   public static <S extends AvailabilityState> void publish(ApplicationEventPublisher publisher, Object source, S state) {
      Assert.notNull(publisher, "'publisher' must not be null");
      publisher.publishEvent(new AvailabilityChangeEvent(source, state));
   }
}
