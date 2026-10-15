package org.springframework.expression.spel.support;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.MethodExecutor;
import org.springframework.expression.TypedValue;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

public class ReflectiveMethodExecutor implements MethodExecutor {
   private final Method originalMethod;
   private final Method methodToInvoke;
   private final @Nullable Integer varargsPosition;
   private final @Nullable Class<?> publicDeclaringClass;
   private boolean argumentConversionOccurred = false;

   public ReflectiveMethodExecutor(Method method) {
      this(method, null);
   }

   public ReflectiveMethodExecutor(Method method, @Nullable Class<?> targetClass) {
      this.originalMethod = method;
      this.methodToInvoke = ClassUtils.getPubliclyAccessibleMethodIfPossible(method, targetClass);
      Class<?> declaringClass = this.methodToInvoke.getDeclaringClass();
      this.publicDeclaringClass = Modifier.isPublic(declaringClass.getModifiers()) ? declaringClass : null;
      this.varargsPosition = method.isVarArgs() ? method.getParameterCount() - 1 : null;
   }

   public final Method getMethod() {
      return this.originalMethod;
   }

   public @Nullable Class<?> getPublicDeclaringClass() {
      return this.publicDeclaringClass;
   }

   public boolean didArgumentConversionOccur() {
      return this.argumentConversionOccurred;
   }

   @Override
   public TypedValue execute(EvaluationContext context, Object target, @Nullable Object... arguments) throws AccessException {
      try {
         this.argumentConversionOccurred = ReflectionHelper.convertArguments(context.getTypeConverter(), arguments, this.originalMethod, this.varargsPosition);
         if (this.originalMethod.isVarArgs()) {
            arguments = ReflectionHelper.setupArgumentsForVarargsInvocation(this.originalMethod.getParameterTypes(), arguments);
         }

         ReflectionUtils.makeAccessible(this.methodToInvoke);
         Object value = this.methodToInvoke.invoke(target, arguments);
         return new TypedValue(value, new TypeDescriptor(new MethodParameter(this.originalMethod, -1)).narrow(value));
      } catch (Exception ex) {
         throw new AccessException("Problem invoking method: " + this.methodToInvoke, ex);
      }
   }
}
