package org.springframework.expression;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.TypeDescriptor;

@FunctionalInterface
public interface MethodResolver {
   @Nullable MethodExecutor resolve(EvaluationContext context, Object targetObject, String name, List<TypeDescriptor> argumentTypes) throws AccessException;
}
