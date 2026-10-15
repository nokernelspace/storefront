package org.springframework.aop.support;

import org.jspecify.annotations.Nullable;
import org.springframework.aop.Pointcut;

public interface ExpressionPointcut extends Pointcut {
   @Nullable String getExpression();
}
