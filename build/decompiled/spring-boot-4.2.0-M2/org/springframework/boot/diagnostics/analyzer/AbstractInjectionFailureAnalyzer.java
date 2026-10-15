package org.springframework.boot.diagnostics.analyzer;

import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

public abstract class AbstractInjectionFailureAnalyzer<T extends Throwable> extends AbstractFailureAnalyzer<T> {
   @Override
   protected final @Nullable FailureAnalysis analyze(Throwable rootFailure, T cause) {
      return this.analyze(rootFailure, cause, this.getDescription(rootFailure));
   }

   private @Nullable String getDescription(Throwable rootFailure) {
      UnsatisfiedDependencyException unsatisfiedDependency = this.findMostNestedCause(rootFailure, UnsatisfiedDependencyException.class);
      if (unsatisfiedDependency != null) {
         return this.getDescription(unsatisfiedDependency);
      }

      BeanInstantiationException beanInstantiationException = this.findMostNestedCause(rootFailure, BeanInstantiationException.class);
      return beanInstantiationException != null ? this.getDescription(beanInstantiationException) : null;
   }

   private <C extends Exception> @Nullable C findMostNestedCause(Throwable root, Class<C> type) {
      Throwable candidate = root;
      C result = null;

      while (candidate != null) {
         if (type.isInstance(candidate)) {
            result = (C)candidate;
         }

         candidate = candidate.getCause();
      }

      return result;
   }

   private @Nullable String getDescription(UnsatisfiedDependencyException ex) {
      InjectionPoint injectionPoint = ex.getInjectionPoint();
      if (injectionPoint != null) {
         if (injectionPoint.getField() != null) {
            return String.format("Field %s in %s", injectionPoint.getField().getName(), injectionPoint.getField().getDeclaringClass().getName());
         }

         if (injectionPoint.getMethodParameter() != null) {
            if (injectionPoint.getMethodParameter().getConstructor() != null) {
               return String.format(
                  "Parameter %d of constructor in %s",
                  injectionPoint.getMethodParameter().getParameterIndex(),
                  injectionPoint.getMethodParameter().getDeclaringClass().getName()
               );
            }

            Method method = injectionPoint.getMethodParameter().getMethod();
            Assert.state(method != null, "Neither constructor nor method is available");
            return String.format(
               "Parameter %d of method %s in %s",
               injectionPoint.getMethodParameter().getParameterIndex(),
               method.getName(),
               injectionPoint.getMethodParameter().getDeclaringClass().getName()
            );
         }
      }

      return ex.getResourceDescription();
   }

   private String getDescription(BeanInstantiationException ex) {
      if (ex.getConstructingMethod() != null) {
         return String.format("Method %s in %s", ex.getConstructingMethod().getName(), ex.getConstructingMethod().getDeclaringClass().getName());
      } else {
         return ex.getConstructor() != null
            ? String.format("Constructor in %s", ClassUtils.getUserClass(ex.getConstructor().getDeclaringClass()).getName())
            : ex.getBeanClass().getName();
      }
   }

   protected abstract @Nullable FailureAnalysis analyze(Throwable rootFailure, T cause, @Nullable String description);
}
