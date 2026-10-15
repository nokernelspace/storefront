package org.springframework.context.expression;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;
import org.springframework.core.KotlinDetector;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.ObjectUtils;

public class MethodBasedEvaluationContext extends StandardEvaluationContext {
   private final Method method;
   private final @Nullable Object[] arguments;
   private final ParameterNameDiscoverer parameterNameDiscoverer;
   private boolean argumentsLoaded = false;

   public MethodBasedEvaluationContext(
      @Nullable Object rootObject, Method method, @Nullable Object[] arguments, ParameterNameDiscoverer parameterNameDiscoverer
   ) {
      super(rootObject);
      this.method = method;
      this.arguments = KotlinDetector.isSuspendingFunction(method) ? Arrays.copyOf(arguments, arguments.length - 1) : arguments;
      this.parameterNameDiscoverer = parameterNameDiscoverer;
   }

   public @Nullable Object lookupVariable(String name) {
      Object variable = super.lookupVariable(name);
      if (variable != null) {
         return variable;
      }

      if (!this.argumentsLoaded) {
         this.lazyLoadArguments();
         this.argumentsLoaded = true;
         variable = super.lookupVariable(name);
      }

      return variable;
   }

   protected void lazyLoadArguments() {
      if (!ObjectUtils.isEmpty(this.arguments)) {
         String[] paramNames = this.parameterNameDiscoverer.getParameterNames(this.method);
         int paramCount = paramNames != null ? paramNames.length : this.method.getParameterCount();
         int argsCount = this.arguments.length;

         for (int i = 0; i < paramCount; i++) {
            Object value = null;
            if (argsCount > paramCount && i == paramCount - 1) {
               value = Arrays.copyOfRange(this.arguments, i, argsCount);
            } else if (argsCount > i) {
               value = this.arguments[i];
            }

            this.setVariable("a" + i, value);
            this.setVariable("p" + i, value);
            if (paramNames != null && paramNames[i] != null) {
               this.setVariable(paramNames[i], value);
            }
         }
      }
   }
}
