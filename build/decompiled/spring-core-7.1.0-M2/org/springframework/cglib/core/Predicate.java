package org.springframework.cglib.core;

@FunctionalInterface
public interface Predicate {
   boolean evaluate(Object arg);
}
