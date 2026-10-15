package org.springframework.context.expression;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;
import org.springframework.util.Assert;

public class BeanExpressionContextAccessor implements PropertyAccessor {
   public Class<?>[] getSpecificTargetClasses() {
      return new Class[]{BeanExpressionContext.class};
   }

   public boolean canRead(EvaluationContext context, @Nullable Object target, String name) throws AccessException {
      return target instanceof BeanExpressionContext bec && bec.containsObject(name);
   }

   public TypedValue read(EvaluationContext context, @Nullable Object target, String name) throws AccessException {
      Assert.state(target instanceof BeanExpressionContext, "Target must be of type BeanExpressionContext");
      return new TypedValue(((BeanExpressionContext)target).getObject(name));
   }

   public boolean canWrite(EvaluationContext context, @Nullable Object target, String name) throws AccessException {
      return false;
   }

   public void write(EvaluationContext context, @Nullable Object target, String name, @Nullable Object newValue) throws AccessException {
      throw new AccessException("Beans in a BeanFactory are read-only");
   }
}
