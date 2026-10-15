package org.springframework.cache.interceptor;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;
import org.springframework.context.expression.AnnotatedElementKey;
import org.springframework.context.expression.CachedExpressionEvaluator;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;

class CacheOperationExpressionEvaluator extends CachedExpressionEvaluator {
   public static final Object NO_RESULT = new Object();
   public static final Object RESULT_UNAVAILABLE = new Object();
   public static final String RESULT_VARIABLE = "result";
   private final Map<CachedExpressionEvaluator.ExpressionKey, Expression> keyCache = new ConcurrentHashMap<>(64);
   private final Map<CachedExpressionEvaluator.ExpressionKey, Expression> conditionCache = new ConcurrentHashMap<>(64);
   private final Map<CachedExpressionEvaluator.ExpressionKey, Expression> unlessCache = new ConcurrentHashMap<>(64);
   private final CacheEvaluationContextFactory evaluationContextFactory;

   public CacheOperationExpressionEvaluator(CacheEvaluationContextFactory evaluationContextFactory) {
      this.evaluationContextFactory = evaluationContextFactory;
      this.evaluationContextFactory.setParameterNameDiscoverer(() -> this.getParameterNameDiscoverer());
   }

   public EvaluationContext createEvaluationContext(
      Collection<? extends Cache> caches,
      Method method,
      @Nullable Object[] args,
      Object target,
      Class<?> targetClass,
      Method targetMethod,
      @Nullable Object result
   ) {
      CacheExpressionRootObject rootObject = new CacheExpressionRootObject(caches, method, args, target, targetClass);
      CacheEvaluationContext evaluationContext = this.evaluationContextFactory.forOperation(rootObject, targetMethod, args);
      if (result == RESULT_UNAVAILABLE) {
         evaluationContext.addUnavailableVariable("result");
      } else if (result != NO_RESULT) {
         evaluationContext.setVariable("result", result);
      }

      return evaluationContext;
   }

   public @Nullable Object key(String keyExpression, AnnotatedElementKey methodKey, EvaluationContext evalContext) {
      return this.getExpression(this.keyCache, methodKey, keyExpression).getValue(evalContext);
   }

   public boolean condition(String conditionExpression, AnnotatedElementKey methodKey, EvaluationContext evalContext) {
      return Boolean.TRUE.equals(this.getExpression(this.conditionCache, methodKey, conditionExpression).getValue(evalContext, Boolean.class));
   }

   public boolean unless(String unlessExpression, AnnotatedElementKey methodKey, EvaluationContext evalContext) {
      return Boolean.TRUE.equals(this.getExpression(this.unlessCache, methodKey, unlessExpression).getValue(evalContext, Boolean.class));
   }

   void clear() {
      this.keyCache.clear();
      this.conditionCache.clear();
      this.unlessCache.clear();
   }
}
