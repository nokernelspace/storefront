package org.springframework.context.annotation;

import org.jspecify.annotations.Nullable;
import org.springframework.core.type.AnnotationMetadata;

interface ImportRegistry {
   @Nullable AnnotationMetadata getImportingClassFor(String importedClass);

   void removeImportingClass(String importingClass);
}
