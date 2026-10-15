package org.springframework.boot;

@FunctionalInterface
public interface ExitCodeGenerator {
   int getExitCode();
}
