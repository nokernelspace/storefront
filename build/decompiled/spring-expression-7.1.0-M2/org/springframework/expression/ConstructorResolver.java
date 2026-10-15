package org.springframework.expression;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.TypeDescriptor;

@FunctionalInterface
public interface ConstructorResolver {
   @Nullable ConstructorExecutor resolve(EvaluationContext context, String typeName, List<TypeDescriptor> argumentTypes) throws AccessException;
}
