package org.springframework.beans.factory.parsing;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

public class FailFastProblemReporter implements ProblemReporter {
   private Log logger = LogFactory.getLog(this.getClass());

   public void setLogger(@Nullable Log logger) {
      this.logger = logger != null ? logger : LogFactory.getLog(this.getClass());
   }

   @Override
   public void fatal(Problem problem) {
      throw new BeanDefinitionParsingException(problem);
   }

   @Override
   public void error(Problem problem) {
      throw new BeanDefinitionParsingException(problem);
   }

   @Override
   public void warning(Problem problem) {
      this.logger.warn(problem, problem.getRootCause());
   }
}
