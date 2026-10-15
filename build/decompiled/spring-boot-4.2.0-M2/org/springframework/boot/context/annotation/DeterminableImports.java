package org.springframework.boot.context.annotation;

import java.util.Set;
import org.springframework.core.type.AnnotationMetadata;

@FunctionalInterface
public interface DeterminableImports {
   Set<Object> determineImports(AnnotationMetadata metadata);
}
