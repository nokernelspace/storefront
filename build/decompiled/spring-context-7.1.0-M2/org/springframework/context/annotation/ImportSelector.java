package org.springframework.context.annotation;

import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.core.type.AnnotationMetadata;

public interface ImportSelector {
   String[] selectImports(AnnotationMetadata importingClassMetadata);

   default @Nullable Predicate<String> getExclusionFilter() {
      return null;
   }
}
