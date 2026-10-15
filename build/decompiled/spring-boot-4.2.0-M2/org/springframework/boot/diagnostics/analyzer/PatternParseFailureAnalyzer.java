package org.springframework.boot.diagnostics.analyzer;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.web.util.pattern.PatternParseException;

class PatternParseFailureAnalyzer extends AbstractFailureAnalyzer<PatternParseException> {
   protected FailureAnalysis analyze(Throwable rootFailure, PatternParseException cause) {
      return new FailureAnalysis(
         "Invalid mapping pattern detected:\n" + cause.toDetailedString(),
         "Fix this pattern in your application or switch to the legacy parser implementation with 'spring.mvc.pathmatch.matching-strategy=ant_path_matcher'.",
         cause
      );
   }
}
