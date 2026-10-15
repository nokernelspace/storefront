package org.springframework.beans.factory.config;

import org.jspecify.annotations.Nullable;
import org.springframework.util.StringValueResolver;

public class EmbeddedValueResolver implements StringValueResolver {
   private final BeanExpressionContext exprContext;
   private final @Nullable BeanExpressionResolver exprResolver;

   public EmbeddedValueResolver(ConfigurableBeanFactory beanFactory) {
      this.exprContext = new BeanExpressionContext(beanFactory, null);
      this.exprResolver = beanFactory.getBeanExpressionResolver();
   }

   public @Nullable String resolveStringValue(String strVal) {
      String value = this.exprContext.getBeanFactory().resolveEmbeddedValue(strVal);
      if (this.exprResolver != null && value != null) {
         Object evaluated = this.exprResolver.evaluate(value, this.exprContext);
         value = evaluated != null ? evaluated.toString() : null;
      }

      return value;
   }
}
