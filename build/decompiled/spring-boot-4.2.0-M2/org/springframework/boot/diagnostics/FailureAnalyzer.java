package org.springframework.boot.diagnostics;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface FailureAnalyzer {
   @Nullable FailureAnalysis analyze(Throwable failure);
}
