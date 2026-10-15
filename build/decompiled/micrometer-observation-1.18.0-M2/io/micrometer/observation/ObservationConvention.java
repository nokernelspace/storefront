package io.micrometer.observation;

import io.micrometer.common.KeyValues;
import org.jspecify.annotations.Nullable;

public interface ObservationConvention<T extends Observation.Context> extends KeyValuesConvention {
   ObservationConvention<Observation.Context> EMPTY = context -> false;

   default KeyValues getLowCardinalityKeyValues(T context) {
      return KeyValues.empty();
   }

   default KeyValues getHighCardinalityKeyValues(T context) {
      return KeyValues.empty();
   }

   boolean supportsContext(Observation.Context var1);

   default @Nullable String getName() {
      return null;
   }

   default @Nullable String getContextualName(T context) {
      return null;
   }
}
