package org.springframework.expression;

import org.jspecify.annotations.Nullable;

public interface TargetedAccessor {
   Class<?> @Nullable [] getSpecificTargetClasses();
}
