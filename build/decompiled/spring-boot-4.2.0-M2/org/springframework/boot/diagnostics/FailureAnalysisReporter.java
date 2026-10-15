package org.springframework.boot.diagnostics;

@FunctionalInterface
public interface FailureAnalysisReporter {
   void report(FailureAnalysis analysis);
}
