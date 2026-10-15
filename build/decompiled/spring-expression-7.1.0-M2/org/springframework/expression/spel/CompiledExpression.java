package org.springframework.expression.spel;

import org.jspecify.annotations.Nullable;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;

public abstract class CompiledExpression {
   public abstract Object getValue(@Nullable Object target, @Nullable EvaluationContext context) throws EvaluationException;
}
