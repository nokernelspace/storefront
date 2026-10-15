package org.springframework.context.expression;

import org.jspecify.annotations.Nullable;
import org.springframework.core.env.Environment;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;
import org.springframework.util.Assert;

public class EnvironmentAccessor implements PropertyAccessor {
   public Class<?>[] getSpecificTargetClasses() {
      return new Class[]{Environment.class};
   }

   public boolean canRead(EvaluationContext context, @Nullable Object target, String name) throws AccessException {
      return target instanceof Environment;
   }

   public TypedValue read(EvaluationContext context, @Nullable Object target, String name) throws AccessException {
      Assert.state(target instanceof Environment, "Target must be of type Environment");
      return new TypedValue(((Environment)target).getProperty(name));
   }

   public boolean canWrite(EvaluationContext context, @Nullable Object target, String name) throws AccessException {
      return false;
   }

   public void write(EvaluationContext context, @Nullable Object target, String name, @Nullable Object newValue) throws AccessException {
      throw new AccessException("The Environment is read-only");
   }
}
