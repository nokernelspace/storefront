package io.micrometer.observation.transport;

import java.util.Collections;
import org.jspecify.annotations.Nullable;

public interface Propagator {
   interface Getter<C> {
      @Nullable String get(C var1, String var2);

      default Iterable<String> getAll(C carrier, String key) {
         String firstValue = this.get(carrier, key);
         return firstValue == null ? Collections.emptyList() : Collections.singletonList(firstValue);
      }
   }

   interface Setter<C> {
      void set(@Nullable C var1, String var2, String var3);
   }
}
