package org.springframework.expression;

@FunctionalInterface
public interface TypeLocator {
   Class<?> findType(String typeName) throws EvaluationException;
}
