package org.springframework.expression;

import org.jspecify.annotations.Nullable;

public interface IndexAccessor extends TargetedAccessor {
   boolean canRead(EvaluationContext context, Object target, Object index) throws AccessException;

   TypedValue read(EvaluationContext context, Object target, Object index) throws AccessException;

   boolean canWrite(EvaluationContext context, Object target, Object index) throws AccessException;

   void write(EvaluationContext context, Object target, Object index, @Nullable Object newValue) throws AccessException;
}
