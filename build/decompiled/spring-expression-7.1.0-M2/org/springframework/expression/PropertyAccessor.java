package org.springframework.expression;

import org.jspecify.annotations.Nullable;

public interface PropertyAccessor extends TargetedAccessor {
   boolean canRead(EvaluationContext context, @Nullable Object target, String name) throws AccessException;

   TypedValue read(EvaluationContext context, @Nullable Object target, String name) throws AccessException;

   boolean canWrite(EvaluationContext context, @Nullable Object target, String name) throws AccessException;

   void write(EvaluationContext context, @Nullable Object target, String name, @Nullable Object newValue) throws AccessException;
}
