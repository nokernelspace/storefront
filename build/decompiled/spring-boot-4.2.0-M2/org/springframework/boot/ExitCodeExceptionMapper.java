package org.springframework.boot;

@FunctionalInterface
public interface ExitCodeExceptionMapper {
   int getExitCode(Throwable exception);
}
