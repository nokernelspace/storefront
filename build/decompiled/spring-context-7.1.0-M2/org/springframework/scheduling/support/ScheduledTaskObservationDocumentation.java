package org.springframework.scheduling.support;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.docs.ObservationDocumentation;

public enum ScheduledTaskObservationDocumentation implements ObservationDocumentation {
   TASKS_SCHEDULED_EXECUTION {
      public Class<? extends ObservationConvention<? extends Context>> getDefaultConvention() {
         return DefaultScheduledTaskObservationConvention.class;
      }

      public KeyName[] getLowCardinalityKeyNames() {
         return ScheduledTaskObservationDocumentation.LowCardinalityKeyNames.values();
      }

      public KeyName[] getHighCardinalityKeyNames() {
         return new KeyName[0];
      }
   };

   public enum LowCardinalityKeyNames implements KeyName {
      CODE_FUNCTION {
         public String asString() {
            return "code.function";
         }
      },
      CODE_NAMESPACE {
         public String asString() {
            return "code.namespace";
         }
      },
      EXCEPTION {
         public String asString() {
            return "exception";
         }
      },
      OUTCOME {
         public String asString() {
            return "outcome";
         }
      };
   }
}
