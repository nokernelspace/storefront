package org.springframework.expression;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface MethodExecutor {
   TypedValue execute(EvaluationContext context, Object target, @Nullable Object... arguments) throws AccessException;
}
