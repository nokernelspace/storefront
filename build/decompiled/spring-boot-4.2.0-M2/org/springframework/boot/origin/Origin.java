package org.springframework.boot.origin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public interface Origin {
   default @Nullable Origin getParent() {
      return null;
   }

   static @Nullable Origin from(@Nullable Object source) {
      if (source instanceof Origin origin) {
         return origin;
      } else {
         Origin origin = null;
         if (source instanceof OriginProvider originProvider) {
            origin = originProvider.getOrigin();
         }

         return origin == null && source instanceof Throwable throwable ? from(throwable.getCause()) : origin;
      }
   }

   static List<Origin> parentsFrom(@Nullable Object source) {
      Origin origin = from(source);
      if (origin == null) {
         return Collections.emptyList();
      }

      Set<Origin> parents = new LinkedHashSet<>();

      for (Origin var3 = origin.getParent(); var3 != null && !parents.contains(var3); var3 = var3.getParent()) {
         parents.add(var3);
      }

      return Collections.unmodifiableList(new ArrayList<>(parents));
   }
}
