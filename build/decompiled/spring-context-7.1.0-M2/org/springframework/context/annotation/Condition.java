package org.springframework.context.annotation;

import org.springframework.core.type.AnnotatedTypeMetadata;

@FunctionalInterface
public interface Condition {
   boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata);
}
