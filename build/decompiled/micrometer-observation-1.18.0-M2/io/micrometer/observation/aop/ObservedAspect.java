package io.micrometer.observation.aop;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.Observations;
import io.micrometer.observation.annotation.Observed;
import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;
import org.aspectj.lang.NoAspectBoundException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

@Aspect
public class ObservedAspect {
   private static final Predicate<ProceedingJoinPoint> DONT_SKIP_ANYTHING = pjp -> false;
   private final ObservationRegistry registry;
   private final @Nullable ObservationConvention<ObservedAspect.ObservedAspectContext> observationConvention;
   private final Predicate<ProceedingJoinPoint> shouldSkip;
   private @Nullable ObservationKeyValueAnnotationHandler observationKeyValueAnnotationHandler;

   public ObservedAspect() {
      this(Observations.getGlobalRegistry(), null, DONT_SKIP_ANYTHING);
   }

   public ObservedAspect(ObservationRegistry registry) {
      this(registry, null, DONT_SKIP_ANYTHING);
   }

   public ObservedAspect(ObservationRegistry registry, ObservationConvention<ObservedAspect.ObservedAspectContext> observationConvention) {
      this(registry, observationConvention, DONT_SKIP_ANYTHING);
   }

   public ObservedAspect(ObservationRegistry registry, Predicate<ProceedingJoinPoint> shouldSkip) {
      this(registry, null, shouldSkip);
   }

   public ObservedAspect(
      ObservationRegistry registry,
      @Nullable ObservationConvention<ObservedAspect.ObservedAspectContext> observationConvention,
      Predicate<ProceedingJoinPoint> shouldSkip
   ) {
      this.registry = registry;
      this.observationConvention = observationConvention;
      this.shouldSkip = shouldSkip;
   }

   @Around("@within(io.micrometer.observation.annotation.Observed) && !@annotation(io.micrometer.observation.annotation.Observed) && execution(* *.*(..))")
   @NullUnmarked
   public Object observeClass(ProceedingJoinPoint pjp) throws Throwable {
      if (ajc$inlineAccessFieldGet$io_micrometer_observation_aop_ObservedAspect$io_micrometer_observation_aop_ObservedAspect$shouldSkip(this).test(pjp)) {
         return pjp.proceed();
      }

      Method method = ((MethodSignature)pjp.getSignature()).getMethod();
      Observed observed = ajc$inlineAccessMethod$io_micrometer_observation_aop_ObservedAspect$io_micrometer_observation_aop_ObservedAspect$getDeclaringClass(
            this, pjp
         )
         .getAnnotation(Observed.class);
      return ajc$inlineAccessMethod$io_micrometer_observation_aop_ObservedAspect$io_micrometer_observation_aop_ObservedAspect$observe(
         this, pjp, method, observed
      );
   }

   @Around("execution (@io.micrometer.observation.annotation.Observed * *.*(..))")
   @NullUnmarked
   public Object observeMethod(ProceedingJoinPoint pjp) throws Throwable {
      if (ajc$inlineAccessFieldGet$io_micrometer_observation_aop_ObservedAspect$io_micrometer_observation_aop_ObservedAspect$shouldSkip(this).test(pjp)) {
         return pjp.proceed();
      }

      Method method = ajc$inlineAccessMethod$io_micrometer_observation_aop_ObservedAspect$io_micrometer_observation_aop_ObservedAspect$getMethod(this, pjp);
      Observed observed = method.getAnnotation(Observed.class);
      return ajc$inlineAccessMethod$io_micrometer_observation_aop_ObservedAspect$io_micrometer_observation_aop_ObservedAspect$observe(
         this, pjp, method, observed
      );
   }

   @NullUnmarked
   private Object observe(ProceedingJoinPoint pjp, Method method, Observed observed) throws Throwable {
      Observation observation = ObservedAspectObservationDocumentation.of(pjp, observed, this.registry, this.observationConvention);
      if (this.observationKeyValueAnnotationHandler != null) {
         this.observationKeyValueAnnotationHandler.addAnnotatedParameters(observation.getContext(), pjp);
      }

      observation.start();

      try {
         Observation.Scope scope = observation.openScope();

         CompletionStage var8;
         label57: {
            CompletionStage<?> stage;
            try {
               Object result = pjp.proceed();
               if (result != null && CompletionStage.class.isAssignableFrom(method.getReturnType())) {
                  stage = (CompletionStage<?>)result;
                  var8 = stage.whenComplete((res, errorx) -> this.stopObservation(observation, pjp, res, errorx));
                  break label57;
               }

               this.stopObservation(observation, pjp, result, null);
               stage = (CompletionStage<?>)result;
            } catch (Throwable var10) {
               if (scope != null) {
                  try {
                     scope.close();
                  } catch (Throwable var9) {
                     var10.addSuppressed(var9);
                  }
               }

               throw var10;
            }

            if (scope != null) {
               scope.close();
            }

            return stage;
         }

         if (scope != null) {
            scope.close();
         }

         return var8;
      } catch (Throwable error) {
         this.stopObservation(observation, pjp, null, error);
         throw error;
      }
   }

   private Class<?> getDeclaringClass(ProceedingJoinPoint pjp) {
      Method method = ((MethodSignature)pjp.getSignature()).getMethod();
      Class<?> declaringClass = method.getDeclaringClass();
      return !declaringClass.isAnnotationPresent(Observed.class) ? pjp.getTarget().getClass() : declaringClass;
   }

   private Method getMethod(ProceedingJoinPoint pjp) throws NoSuchMethodException {
      Method method = ((MethodSignature)pjp.getSignature()).getMethod();
      return method.getAnnotation(Observed.class) == null ? pjp.getTarget().getClass().getMethod(method.getName(), method.getParameterTypes()) : method;
   }

   private void stopObservation(Observation observation, ProceedingJoinPoint pjp, @Nullable Object result, @Nullable Throwable error) {
      if (this.observationKeyValueAnnotationHandler != null) {
         this.observationKeyValueAnnotationHandler.addAnnotatedMethodResult(observation.getContext(), pjp, result);
      }

      if (error != null) {
         observation.error(error);
      }

      observation.stop();
   }

   public void setObservationKeyValueAnnotationHandler(ObservationKeyValueAnnotationHandler observationKeyValueAnnotationHandler) {
      this.observationKeyValueAnnotationHandler = observationKeyValueAnnotationHandler;
   }

   static {
      try {
         ajc$postClinit();
      } catch (Throwable var1) {
         ajc$initFailureCause = var1;
      }
   }

   public static ObservedAspect aspectOf() {
      if (ajc$perSingletonInstance == null) {
         throw new NoAspectBoundException("io.micrometer.observation.aop.ObservedAspect", ajc$initFailureCause);
      } else {
         return ajc$perSingletonInstance;
      }
   }

   public static boolean hasAspect() {
      return ajc$perSingletonInstance != null;
   }

   public static class ObservedAspectContext extends Observation.Context {
      private final ProceedingJoinPoint proceedingJoinPoint;

      public ObservedAspectContext(ProceedingJoinPoint proceedingJoinPoint) {
         this.proceedingJoinPoint = proceedingJoinPoint;
      }

      public ProceedingJoinPoint getProceedingJoinPoint() {
         return this.proceedingJoinPoint;
      }
   }
}
