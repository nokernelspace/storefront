package org.springframework.expression;

@FunctionalInterface
public interface BeanResolver {
   Object resolve(EvaluationContext context, String beanName) throws AccessException;
}
