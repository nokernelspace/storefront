package org.springframework.scheduling.support;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.SchedulingAwareRunnable;
import org.springframework.util.ReflectionUtils;

public class ScheduledMethodRunnable implements SchedulingAwareRunnable {
   private static final ScheduledTaskObservationConvention DEFAULT_CONVENTION = new DefaultScheduledTaskObservationConvention();
   private final Object target;
   private final Method method;
   private final @Nullable String qualifier;
   private final Supplier<ObservationRegistry> observationRegistrySupplier;

   public ScheduledMethodRunnable(Object target, Method method, @Nullable String qualifier, Supplier<ObservationRegistry> observationRegistrySupplier) {
      this.target = target;
      this.method = method;
      this.qualifier = qualifier;
      this.observationRegistrySupplier = observationRegistrySupplier;
   }

   public ScheduledMethodRunnable(Object target, Method method) {
      this(target, method, null, () -> ObservationRegistry.NOOP);
   }

   public ScheduledMethodRunnable(Object target, String methodName) throws NoSuchMethodException {
      this(target, target.getClass().getMethod(methodName));
   }

   public Object getTarget() {
      return this.target;
   }

   public Method getMethod() {
      return this.method;
   }

   @Override
   public @Nullable String getQualifier() {
      return this.qualifier;
   }

   @Override
   public void run() {
      ScheduledTaskObservationContext context = new ScheduledTaskObservationContext(this.target, this.method);
      Observation observation = ScheduledTaskObservationDocumentation.TASKS_SCHEDULED_EXECUTION
         .observation(null, DEFAULT_CONVENTION, () -> context, this.observationRegistrySupplier.get());
      observation.observe(() -> this.runInternal(context));
   }

   private void runInternal(ScheduledTaskObservationContext context) {
      try {
         ReflectionUtils.makeAccessible(this.method);
         this.method.invoke(this.target);
         context.setComplete(true);
      } catch (InvocationTargetException ex) {
         ReflectionUtils.rethrowRuntimeException(ex.getTargetException());
      } catch (IllegalAccessException ex) {
         throw new UndeclaredThrowableException(ex);
      }
   }

   @Override
   public String toString() {
      return this.method.getDeclaringClass().getName() + "." + this.method.getName();
   }
}
