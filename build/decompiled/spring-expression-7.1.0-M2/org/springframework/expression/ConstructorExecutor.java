package org.springframework.expression;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface ConstructorExecutor {
   TypedValue execute(EvaluationContext context, @Nullable Object... arguments) throws AccessException;
}
