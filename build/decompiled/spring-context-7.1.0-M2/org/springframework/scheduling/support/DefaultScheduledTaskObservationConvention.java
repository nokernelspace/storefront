package org.springframework.scheduling.support;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import org.springframework.util.StringUtils;

public class DefaultScheduledTaskObservationConvention implements ScheduledTaskObservationConvention {
   private static final String DEFAULT_NAME = "tasks.scheduled.execution";
   private static final KeyValue EXCEPTION_NONE = KeyValue.of(ScheduledTaskObservationDocumentation.LowCardinalityKeyNames.EXCEPTION, "none");
   private static final KeyValue OUTCOME_SUCCESS = KeyValue.of(ScheduledTaskObservationDocumentation.LowCardinalityKeyNames.OUTCOME, "SUCCESS");
   private static final KeyValue OUTCOME_ERROR = KeyValue.of(ScheduledTaskObservationDocumentation.LowCardinalityKeyNames.OUTCOME, "ERROR");
   private static final KeyValue OUTCOME_UNKNOWN = KeyValue.of(ScheduledTaskObservationDocumentation.LowCardinalityKeyNames.OUTCOME, "UNKNOWN");
   private static final KeyValue CODE_NAMESPACE_ANONYMOUS = KeyValue.of(
      ScheduledTaskObservationDocumentation.LowCardinalityKeyNames.CODE_NAMESPACE, "ANONYMOUS"
   );

   public String getName() {
      return "tasks.scheduled.execution";
   }

   public String getContextualName(ScheduledTaskObservationContext context) {
      return "task " + StringUtils.uncapitalize(context.getTargetClass().getSimpleName()) + "." + context.getMethod().getName();
   }

   public KeyValues getLowCardinalityKeyValues(ScheduledTaskObservationContext context) {
      return KeyValues.of(new KeyValue[]{this.codeFunction(context), this.codeNamespace(context), this.exception(context), this.outcome(context)});
   }

   protected KeyValue codeFunction(ScheduledTaskObservationContext context) {
      return KeyValue.of(ScheduledTaskObservationDocumentation.LowCardinalityKeyNames.CODE_FUNCTION, context.getMethod().getName());
   }

   protected KeyValue codeNamespace(ScheduledTaskObservationContext context) {
      return context.getTargetClass().getCanonicalName() != null
         ? KeyValue.of(ScheduledTaskObservationDocumentation.LowCardinalityKeyNames.CODE_NAMESPACE, context.getTargetClass().getCanonicalName())
         : CODE_NAMESPACE_ANONYMOUS;
   }

   protected KeyValue exception(ScheduledTaskObservationContext context) {
      return context.getError() != null
         ? KeyValue.of(ScheduledTaskObservationDocumentation.LowCardinalityKeyNames.EXCEPTION, context.getError().getClass().getSimpleName())
         : EXCEPTION_NONE;
   }

   protected KeyValue outcome(ScheduledTaskObservationContext context) {
      if (context.getError() != null) {
         return OUTCOME_ERROR;
      } else {
         return !context.isComplete() ? OUTCOME_UNKNOWN : OUTCOME_SUCCESS;
      }
   }
}
