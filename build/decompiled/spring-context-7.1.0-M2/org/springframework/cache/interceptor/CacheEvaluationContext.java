package org.springframework.cache.interceptor;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.ParameterNameDiscoverer;

class CacheEvaluationContext extends MethodBasedEvaluationContext {
   private final Set<String> unavailableVariables = new HashSet<>(1);

   CacheEvaluationContext(@Nullable Object rootObject, Method method, @Nullable Object[] arguments, ParameterNameDiscoverer parameterNameDiscoverer) {
      super(rootObject, method, arguments, parameterNameDiscoverer);
   }

   public void addUnavailableVariable(String name) {
      this.unavailableVariables.add(name);
   }

   @Override
   public @Nullable Object lookupVariable(String name) {
      if (this.unavailableVariables.contains(name)) {
         throw new VariableNotAvailableException(name);
      } else {
         return super.lookupVariable(name);
      }
   }
}
