package org.springframework.beans.factory.config;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;

public interface BeanExpressionResolver {
   @Nullable Object evaluate(@Nullable String value, BeanExpressionContext beanExpressionContext) throws BeansException;
}
