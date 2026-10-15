package org.springframework.expression;

import org.jspecify.annotations.Nullable;

public interface TypeComparator {
   boolean canCompare(@Nullable Object firstObject, @Nullable Object secondObject);

   int compare(@Nullable Object firstObject, @Nullable Object secondObject) throws EvaluationException;
}
