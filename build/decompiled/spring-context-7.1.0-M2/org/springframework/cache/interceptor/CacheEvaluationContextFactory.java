package org.springframework.cache.interceptor;

import java.lang.reflect.Method;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.function.SingletonSupplier;

class CacheEvaluationContextFactory {
   private final StandardEvaluationContext originalContext;
   private @Nullable Supplier<ParameterNameDiscoverer> parameterNameDiscoverer;

   CacheEvaluationContextFactory(StandardEvaluationContext originalContext) {
      this.originalContext = originalContext;
   }

   public void setParameterNameDiscoverer(Supplier<ParameterNameDiscoverer> parameterNameDiscoverer) {
      this.parameterNameDiscoverer = parameterNameDiscoverer;
   }

   public ParameterNameDiscoverer getParameterNameDiscoverer() {
      if (this.parameterNameDiscoverer == null) {
         this.parameterNameDiscoverer = SingletonSupplier.of(DefaultParameterNameDiscoverer.getSharedInstance());
      }

      return this.parameterNameDiscoverer.get();
   }

   public CacheEvaluationContext forOperation(CacheExpressionRootObject rootObject, Method targetMethod, @Nullable Object[] args) {
      CacheEvaluationContext evaluationContext = new CacheEvaluationContext(rootObject, targetMethod, args, this.getParameterNameDiscoverer());
      this.originalContext.applyDelegatesTo(evaluationContext);
      return evaluationContext;
   }
}
