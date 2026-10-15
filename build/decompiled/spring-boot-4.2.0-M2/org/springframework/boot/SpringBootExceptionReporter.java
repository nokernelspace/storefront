package org.springframework.boot;

@FunctionalInterface
public interface SpringBootExceptionReporter {
   boolean reportException(Throwable failure);
}
