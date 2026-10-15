package org.springframework.expression;

import org.jspecify.annotations.Nullable;

public interface OperatorOverloader {
   boolean overridesOperation(Operation operation, @Nullable Object leftOperand, @Nullable Object rightOperand) throws EvaluationException;

   Object operate(Operation operation, @Nullable Object leftOperand, @Nullable Object rightOperand) throws EvaluationException;
}
