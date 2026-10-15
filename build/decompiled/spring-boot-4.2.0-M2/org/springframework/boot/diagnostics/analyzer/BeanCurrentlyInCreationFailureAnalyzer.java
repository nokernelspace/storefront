package org.springframework.boot.diagnostics.analyzer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.util.StringUtils;

class BeanCurrentlyInCreationFailureAnalyzer extends AbstractFailureAnalyzer<BeanCurrentlyInCreationException> {
   private final @Nullable AbstractAutowireCapableBeanFactory beanFactory;

   BeanCurrentlyInCreationFailureAnalyzer(BeanFactory beanFactory) {
      if (beanFactory instanceof AbstractAutowireCapableBeanFactory autowireCapableBeanFactory) {
         this.beanFactory = autowireCapableBeanFactory;
      } else {
         this.beanFactory = null;
      }
   }

   protected @Nullable FailureAnalysis analyze(Throwable rootFailure, BeanCurrentlyInCreationException cause) {
      BeanCurrentlyInCreationFailureAnalyzer.DependencyCycle dependencyCycle = this.findCycle(rootFailure);
      return dependencyCycle == null ? null : new FailureAnalysis(this.buildMessage(dependencyCycle), this.action(), cause);
   }

   private String action() {
      return this.beanFactory != null && this.beanFactory.isAllowCircularReferences()
         ? "Despite circular references being allowed, the dependency cycle between beans could not be broken. Update your application to remove the dependency cycle."
         : "Relying upon circular references is discouraged and they are prohibited by default. Update your application to remove the dependency cycle between beans. As a last resort, it may be possible to break the cycle automatically by setting spring.main.allow-circular-references to true.";
   }

   private BeanCurrentlyInCreationFailureAnalyzer.@Nullable DependencyCycle findCycle(Throwable rootFailure) {
      List<BeanCurrentlyInCreationFailureAnalyzer.BeanInCycle> beansInCycle = new ArrayList<>();
      Throwable candidate = rootFailure;
      int cycleStart = -1;

      while (candidate != null) {
         BeanCurrentlyInCreationFailureAnalyzer.BeanInCycle beanInCycle = BeanCurrentlyInCreationFailureAnalyzer.BeanInCycle.get(candidate);
         if (beanInCycle != null) {
            int index = beansInCycle.indexOf(beanInCycle);
            if (index == -1) {
               beansInCycle.add(beanInCycle);
            }

            cycleStart = cycleStart != -1 ? cycleStart : index;
         }

         candidate = candidate.getCause();
      }

      return cycleStart == -1 ? null : new BeanCurrentlyInCreationFailureAnalyzer.DependencyCycle(beansInCycle, cycleStart);
   }

   private String buildMessage(BeanCurrentlyInCreationFailureAnalyzer.DependencyCycle dependencyCycle) {
      StringBuilder message = new StringBuilder();
      message.append(String.format("The dependencies of some of the beans in the application context form a cycle:%n%n"));
      List<BeanCurrentlyInCreationFailureAnalyzer.BeanInCycle> beansInCycle = dependencyCycle.getBeansInCycle();
      boolean singleBean = beansInCycle.size() == 1;
      int cycleStart = dependencyCycle.getCycleStart();

      for (int i = 0; i < beansInCycle.size(); i++) {
         BeanCurrentlyInCreationFailureAnalyzer.BeanInCycle beanInCycle = beansInCycle.get(i);
         if (i == cycleStart) {
            message.append(String.format(singleBean ? "┌──->──┐%n" : "┌─────┐%n"));
         } else if (i > 0) {
            String leftSide = i < cycleStart ? " " : "↑";
            message.append(String.format("%s     ↓%n", leftSide));
         }

         String leftSide = i < cycleStart ? " " : "|";
         message.append(String.format("%s  %s%n", leftSide, beanInCycle));
      }

      message.append(String.format(singleBean ? "└──<-──┘%n" : "└─────┘%n"));
      return message.toString();
   }

   private static final class BeanInCycle {
      private final @Nullable String name;
      private final String description;

      private BeanInCycle(BeanCreationException ex) {
         this.name = ex.getBeanName();
         this.description = this.determineDescription(ex);
      }

      private String determineDescription(BeanCreationException ex) {
         if (StringUtils.hasText(ex.getResourceDescription())) {
            return String.format(" defined in %s", ex.getResourceDescription());
         }

         InjectionPoint failedInjectionPoint = this.findFailedInjectionPoint(ex);
         return failedInjectionPoint != null && failedInjectionPoint.getField() != null ? String.format(" (field %s)", failedInjectionPoint.getField()) : "";
      }

      private @Nullable InjectionPoint findFailedInjectionPoint(BeanCreationException ex) {
         return ex instanceof UnsatisfiedDependencyException unsatisfiedDependencyException ? unsatisfiedDependencyException.getInjectionPoint() : null;
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         } else {
            return obj != null && this.getClass() == obj.getClass()
               ? Objects.equals(this.name, ((BeanCurrentlyInCreationFailureAnalyzer.BeanInCycle)obj).name)
               : false;
         }
      }

      @Override
      public int hashCode() {
         return Objects.hashCode(this.name);
      }

      @Override
      public String toString() {
         return this.name + this.description;
      }

      static BeanCurrentlyInCreationFailureAnalyzer.@Nullable BeanInCycle get(Throwable ex) {
         return ex instanceof BeanCreationException beanCreationException ? get(beanCreationException) : null;
      }

      private static BeanCurrentlyInCreationFailureAnalyzer.@Nullable BeanInCycle get(BeanCreationException ex) {
         return StringUtils.hasText(ex.getBeanName()) ? new BeanCurrentlyInCreationFailureAnalyzer.BeanInCycle(ex) : null;
      }
   }

   private static final class DependencyCycle {
      private final List<BeanCurrentlyInCreationFailureAnalyzer.BeanInCycle> beansInCycle;
      private final int cycleStart;

      private DependencyCycle(List<BeanCurrentlyInCreationFailureAnalyzer.BeanInCycle> beansInCycle, int cycleStart) {
         this.beansInCycle = beansInCycle;
         this.cycleStart = cycleStart;
      }

      List<BeanCurrentlyInCreationFailureAnalyzer.BeanInCycle> getBeansInCycle() {
         return this.beansInCycle;
      }

      int getCycleStart() {
         return this.cycleStart;
      }
   }
}
