package io.micrometer.observation.aop;

import io.micrometer.common.KeyValues;
import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;
import io.micrometer.observation.docs.ObservationDocumentation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.jspecify.annotations.Nullable;

enum ObservedAspectObservationDocumentation implements ObservationDocumentation {
   DEFAULT;

   static Observation of(
      ProceedingJoinPoint pjp,
      Observed observed,
      ObservationRegistry registry,
      @Nullable ObservationConvention<ObservedAspect.ObservedAspectContext> observationConvention
   ) {
      String name = observed.name().isEmpty() ? "method.observed" : observed.name();
      Signature signature = pjp.getStaticPart().getSignature();
      String contextualName = observed.contextualName().isEmpty()
         ? signature.getDeclaringType().getSimpleName() + "#" + signature.getName()
         : observed.contextualName();
      Observation observation = Observation.createNotStarted(name, () -> new ObservedAspect.ObservedAspectContext(pjp), registry)
         .contextualName(contextualName)
         .lowCardinalityKeyValue(
            ObservedAspectObservationDocumentation.ObservedAspectLowCardinalityKeyName.CLASS_NAME.asString(), signature.getDeclaringTypeName()
         )
         .lowCardinalityKeyValue(ObservedAspectObservationDocumentation.ObservedAspectLowCardinalityKeyName.METHOD_NAME.asString(), signature.getName())
         .lowCardinalityKeyValues(KeyValues.of(observed.lowCardinalityKeyValues()));
      if (observationConvention != null) {
         observation.observationConvention(observationConvention);
      }

      return observation;
   }

   @Override
   public String getName() {
      return "%s";
   }

   @Override
   public String getContextualName() {
      return "%s";
   }

   @Override
   public KeyName[] getLowCardinalityKeyNames() {
      return ObservedAspectObservationDocumentation.ObservedAspectLowCardinalityKeyName.values();
   }

   enum ObservedAspectLowCardinalityKeyName implements KeyName {
      CLASS_NAME {
         public String asString() {
            return "class";
         }
      },
      METHOD_NAME {
         public String asString() {
            return "method";
         }
      };

      ObservedAspectLowCardinalityKeyName() {
      }
   }
}
