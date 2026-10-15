package org.springframework.boot.diagnostics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.SpringBootExceptionReporter;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.io.support.SpringFactoriesLoader.ArgumentResolver;
import org.springframework.core.io.support.SpringFactoriesLoader.FailureHandler;
import org.springframework.core.log.LogMessage;

final class FailureAnalyzers implements SpringBootExceptionReporter {
   private static final Log logger = LogFactory.getLog(FailureAnalyzers.class);
   private final SpringFactoriesLoader springFactoriesLoader;
   private final List<FailureAnalyzer> analyzers;

   public FailureAnalyzers(@Nullable ConfigurableApplicationContext context) {
      this(context, SpringFactoriesLoader.forDefaultResourceLocation(context != null ? context.getClassLoader() : null));
   }

   FailureAnalyzers(@Nullable ConfigurableApplicationContext context, SpringFactoriesLoader springFactoriesLoader) {
      this.springFactoriesLoader = springFactoriesLoader;
      this.analyzers = loadFailureAnalyzers(context, this.springFactoriesLoader);
   }

   private static List<FailureAnalyzer> loadFailureAnalyzers(@Nullable ConfigurableApplicationContext context, SpringFactoriesLoader springFactoriesLoader) {
      List<FailureAnalyzer> analyzers = new ArrayList<>(
         springFactoriesLoader.load(FailureAnalyzer.class, getArgumentResolver(context), FailureHandler.logging(logger))
      );
      analyzers.add(FailureAnalyzedException::analyze);
      return Collections.unmodifiableList(analyzers);
   }

   private static @Nullable ArgumentResolver getArgumentResolver(@Nullable ConfigurableApplicationContext context) {
      if (context == null) {
         return null;
      }

      ArgumentResolver argumentResolver = ArgumentResolver.of(BeanFactory.class, context.getBeanFactory());
      return argumentResolver.and(Environment.class, context.getEnvironment());
   }

   @Override
   public boolean reportException(Throwable failure) {
      FailureAnalysis analysis = this.analyze(failure, this.analyzers);
      return this.report(analysis);
   }

   private @Nullable FailureAnalysis analyze(Throwable failure, List<FailureAnalyzer> analyzers) {
      for (FailureAnalyzer analyzer : analyzers) {
         try {
            FailureAnalysis analysis = analyzer.analyze(failure);
            if (analysis != null) {
               return analysis;
            }
         } catch (Throwable ex) {
            logger.trace(LogMessage.format("FailureAnalyzer %s failed", analyzer), ex);
         }
      }

      return null;
   }

   private boolean report(@Nullable FailureAnalysis analysis) {
      List<FailureAnalysisReporter> reporters = this.springFactoriesLoader.load(FailureAnalysisReporter.class);
      if (analysis != null && !reporters.isEmpty()) {
         for (FailureAnalysisReporter reporter : reporters) {
            reporter.report(analysis);
         }

         return true;
      } else {
         return false;
      }
   }
}
