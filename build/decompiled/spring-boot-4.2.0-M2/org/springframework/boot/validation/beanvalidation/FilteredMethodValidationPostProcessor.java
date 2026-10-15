package org.springframework.boot.validation.beanvalidation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Stream;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.util.Assert;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

public class FilteredMethodValidationPostProcessor extends MethodValidationPostProcessor {
   private final Collection<MethodValidationExcludeFilter> excludeFilters;

   public FilteredMethodValidationPostProcessor(Stream<? extends MethodValidationExcludeFilter> excludeFilters) {
      this.excludeFilters = excludeFilters.map(MethodValidationExcludeFilter.class::cast).toList();
   }

   public FilteredMethodValidationPostProcessor(Collection<? extends MethodValidationExcludeFilter> excludeFilters) {
      this.excludeFilters = new ArrayList<>(excludeFilters);
   }

   public void afterPropertiesSet() {
      super.afterPropertiesSet();
      DefaultPointcutAdvisor advisor = (DefaultPointcutAdvisor)this.advisor;
      Assert.state(advisor != null, "'advisor' must not be null");
      ClassFilter classFilter = advisor.getPointcut().getClassFilter();
      MethodMatcher methodMatcher = advisor.getPointcut().getMethodMatcher();
      advisor.setPointcut(new ComposablePointcut(classFilter, methodMatcher).intersection(this::isIncluded));
   }

   private boolean isIncluded(Class<?> candidate) {
      for (MethodValidationExcludeFilter exclusionFilter : this.excludeFilters) {
         if (exclusionFilter.isExcluded(candidate)) {
            return false;
         }
      }

      return true;
   }
}
