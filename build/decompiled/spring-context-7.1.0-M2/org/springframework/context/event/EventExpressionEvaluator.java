package org.springframework.context.event;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.expression.AnnotatedElementKey;
import org.springframework.context.expression.CachedExpressionEvaluator;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.support.StandardEvaluationContext;

class EventExpressionEvaluator extends CachedExpressionEvaluator {
   private final Map<CachedExpressionEvaluator.ExpressionKey, Expression> conditionCache = new ConcurrentHashMap<>(64);
   private final StandardEvaluationContext originalEvaluationContext;

   EventExpressionEvaluator(StandardEvaluationContext originalEvaluationContext) {
      this.originalEvaluationContext = originalEvaluationContext;
   }

   public boolean condition(String conditionExpression, ApplicationEvent event, Method targetMethod, AnnotatedElementKey methodKey, @Nullable Object[] args) {
      EventExpressionRootObject rootObject = new EventExpressionRootObject(event, args);
      EvaluationContext evaluationContext = this.createEvaluationContext(rootObject, targetMethod, args);
      return Boolean.TRUE.equals(this.getExpression(this.conditionCache, methodKey, conditionExpression).getValue(evaluationContext, Boolean.class));
   }

   private EvaluationContext createEvaluationContext(EventExpressionRootObject rootObject, Method method, @Nullable Object[] args) {
      MethodBasedEvaluationContext evaluationContext = new MethodBasedEvaluationContext(rootObject, method, args, this.getParameterNameDiscoverer());
      this.originalEvaluationContext.applyDelegatesTo(evaluationContext);
      return evaluationContext;
   }
}
