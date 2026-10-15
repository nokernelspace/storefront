package org.springframework.context.expression;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.Assert;

public abstract class CachedExpressionEvaluator {
   private final SpelExpressionParser parser;

   protected CachedExpressionEvaluator() {
      this(new SpelExpressionParser());
   }

   protected CachedExpressionEvaluator(SpelExpressionParser parser) {
      Assert.notNull(parser, "SpelExpressionParser must not be null");
      this.parser = parser;
   }

   protected SpelExpressionParser getParser() {
      return this.parser;
   }

   protected ParameterNameDiscoverer getParameterNameDiscoverer() {
      return DefaultParameterNameDiscoverer.getSharedInstance();
   }

   protected Expression getExpression(Map<CachedExpressionEvaluator.ExpressionKey, Expression> cache, AnnotatedElementKey elementKey, String expression) {
      CachedExpressionEvaluator.ExpressionKey expressionKey = this.createKey(elementKey, expression);
      return cache.computeIfAbsent(expressionKey, key -> this.parseExpression(expression));
   }

   protected Expression parseExpression(String expression) {
      return this.getParser().parseExpression(expression);
   }

   private CachedExpressionEvaluator.ExpressionKey createKey(AnnotatedElementKey elementKey, String expression) {
      return new CachedExpressionEvaluator.ExpressionKey(elementKey, expression);
   }

   protected static class ExpressionKey implements Comparable<CachedExpressionEvaluator.ExpressionKey> {
      private final AnnotatedElementKey element;
      private final String expression;

      protected ExpressionKey(AnnotatedElementKey element, String expression) {
         Assert.notNull(element, "AnnotatedElementKey must not be null");
         Assert.notNull(expression, "Expression must not be null");
         this.element = element;
         this.expression = expression;
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other
            || other instanceof CachedExpressionEvaluator.ExpressionKey that && this.element.equals(that.element) && this.expression.equals(that.expression);
      }

      @Override
      public int hashCode() {
         return this.element.hashCode() * 29 + this.expression.hashCode();
      }

      @Override
      public String toString() {
         return this.element + " with expression \"" + this.expression + "\"";
      }

      public int compareTo(CachedExpressionEvaluator.ExpressionKey other) {
         int result = this.element.toString().compareTo(other.element.toString());
         if (result == 0) {
            result = this.expression.compareTo(other.expression);
         }

         return result;
      }
   }
}
