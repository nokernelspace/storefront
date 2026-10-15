package io.micrometer.common.annotation;

import java.lang.annotation.Annotation;
import org.jspecify.annotations.Nullable;

class AnnotatedObject {
   final Annotation annotation;
   final @Nullable Object object;

   AnnotatedObject(Annotation annotation, @Nullable Object object) {
      this.annotation = annotation;
      this.object = object;
   }
}
