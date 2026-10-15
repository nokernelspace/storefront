package org.springframework.expression.spel.ast;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import org.jspecify.annotations.Nullable;
import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.ExpressionInvocationTargetException;
import org.springframework.expression.MethodExecutor;
import org.springframework.expression.MethodResolver;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.CodeFlow;
import org.springframework.expression.spel.ExpressionState;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.expression.spel.support.ReflectiveMethodExecutor;
import org.springframework.expression.spel.support.ReflectiveMethodResolver;
import org.springframework.util.Assert;

public class MethodReference extends SpelNodeImpl {
   private final boolean nullSafe;
   private final String name;
   private volatile @Nullable Character originalPrimitiveExitTypeDescriptor;
   private volatile boolean unwrapOptional;
   private volatile MethodReference.@Nullable CachedMethodExecutor cachedExecutor;

   public MethodReference(boolean nullSafe, String methodName, int startPos, int endPos, SpelNodeImpl... arguments) {
      super(startPos, endPos, arguments);
      this.name = methodName;
      this.nullSafe = nullSafe;
   }

   @Override
   public final boolean isNullSafe() {
      return this.nullSafe;
   }

   public final String getName() {
      return this.name;
   }

   @Override
   protected ValueRef getValueRef(ExpressionState state) throws EvaluationException {
      Object[] arguments = this.getArguments(state);
      if (state.getActiveContextObject().getValue() == null) {
         if (!this.isNullSafe()) {
            throw this.nullTargetException(this.getArgumentTypes(arguments));
         } else {
            return ValueRef.NullValueRef.INSTANCE;
         }
      } else {
         return new MethodReference.MethodValueRef(state, arguments);
      }
   }

   @Override
   public TypedValue getValueInternal(ExpressionState state) throws EvaluationException {
      EvaluationContext evaluationContext = state.getEvaluationContext();
      TypedValue contextObject = state.getActiveContextObject();
      Object target = contextObject.getValue();
      TypeDescriptor targetType = contextObject.getTypeDescriptor();
      Object[] arguments = this.getArguments(state);
      TypedValue result = this.getValueInternal(state, evaluationContext, target, targetType, arguments);
      this.updateExitTypeDescriptor();
      return result;
   }

   private TypedValue getValueInternal(
      ExpressionState state, EvaluationContext evaluationContext, @Nullable Object target, @Nullable TypeDescriptor targetType, @Nullable Object[] arguments
   ) {
      List<TypeDescriptor> argumentTypes = this.getArgumentTypes(arguments);
      Optional<?> fallbackOptionalTarget = null;
      boolean isEmptyOptional = false;
      this.unwrapOptional = false;
      if (this.isNullSafe()) {
         if (target == null) {
            return TypedValue.NULL;
         }

         if (target instanceof Optional<?> optional) {
            if (optional.isPresent()) {
               target = optional.get();
               fallbackOptionalTarget = optional;
               this.unwrapOptional = true;
            } else {
               isEmptyOptional = true;
            }
         }
      }

      if (target == null) {
         throw this.nullTargetException(argumentTypes);
      }

      MethodExecutor executorToUse = this.getCachedExecutor(evaluationContext, target, targetType, argumentTypes);
      if (executorToUse != null) {
         try {
            state.trackOperation();
            return executorToUse.execute(evaluationContext, target, arguments);
         } catch (AccessException ex) {
            this.throwSimpleExceptionIfPossible(target, ex);
            this.cachedExecutor = null;
            executorToUse = null;
         }
      }

      Object targetToUse = target;
      MethodReference.MethodExecutorSearchResult searchResult = this.findMethodExecutor(argumentTypes, target, evaluationContext);
      if (searchResult.methodExecutor != null) {
         executorToUse = searchResult.methodExecutor;
      } else if (fallbackOptionalTarget != null) {
         searchResult = this.findMethodExecutor(argumentTypes, fallbackOptionalTarget, evaluationContext);
         if (searchResult.methodExecutor != null) {
            executorToUse = searchResult.methodExecutor;
            targetToUse = fallbackOptionalTarget;
            this.unwrapOptional = false;
         }
      } else if (isEmptyOptional) {
         return TypedValue.NULL;
      }

      if (executorToUse == null) {
         String method = FormatHelper.formatMethodForMessage(this.name, argumentTypes);
         String className = FormatHelper.formatClassNameForMessage(target instanceof Class<?> clazz ? clazz : target.getClass());
         if (searchResult.accessException != null) {
            throw new SpelEvaluationException(this.getStartPosition(), searchResult.accessException, SpelMessage.PROBLEM_LOCATING_METHOD, method, className);
         } else {
            throw new SpelEvaluationException(this.getStartPosition(), SpelMessage.METHOD_NOT_FOUND, method, className);
         }
      } else {
         this.cachedExecutor = new MethodReference.CachedMethodExecutor(
            executorToUse, targetToUse instanceof Class<?> clazz ? clazz : null, targetType, argumentTypes
         );

         try {
            state.trackOperation();
            return executorToUse.execute(evaluationContext, targetToUse, arguments);
         } catch (AccessException ex) {
            this.throwSimpleExceptionIfPossible(targetToUse, ex);
            throw new SpelEvaluationException(
               this.getStartPosition(), ex, SpelMessage.EXCEPTION_DURING_METHOD_INVOCATION, this.name, targetToUse.getClass().getName(), ex.getMessage()
            );
         }
      }
   }

   private SpelEvaluationException nullTargetException(List<TypeDescriptor> argumentTypes) {
      return new SpelEvaluationException(
         this.getStartPosition(), SpelMessage.METHOD_CALL_ON_NULL_OBJECT_NOT_ALLOWED, FormatHelper.formatMethodForMessage(this.name, argumentTypes)
      );
   }

   private Object[] getArguments(ExpressionState state) {
      Object[] arguments = new Object[this.getChildCount()];

      for (int i = 0; i < arguments.length; i++) {
         try {
            state.pushActiveContextObject(state.getScopeRootContextObject());
            arguments[i] = this.children[i].getValueInternal(state).getValue();
         } finally {
            state.popActiveContextObject();
         }
      }

      return arguments;
   }

   private List<TypeDescriptor> getArgumentTypes(Object... arguments) {
      List<TypeDescriptor> descriptors = new ArrayList<>(arguments.length);

      for (Object argument : arguments) {
         descriptors.add(TypeDescriptor.forObject(argument));
      }

      return Collections.unmodifiableList(descriptors);
   }

   private @Nullable MethodExecutor getCachedExecutor(
      EvaluationContext evaluationContext, Object target, @Nullable TypeDescriptor targetType, List<TypeDescriptor> argumentTypes
   ) {
      List<MethodResolver> methodResolvers = evaluationContext.getMethodResolvers();
      if (methodResolvers.size() == 1 && methodResolvers.get(0) instanceof ReflectiveMethodResolver) {
         MethodReference.CachedMethodExecutor executorToCheck = this.cachedExecutor;
         if (executorToCheck != null && executorToCheck.isSuitable(target, targetType, argumentTypes)) {
            return executorToCheck.get();
         }

         this.cachedExecutor = null;
         return null;
      } else {
         return null;
      }
   }

   private MethodReference.MethodExecutorSearchResult findMethodExecutor(List<TypeDescriptor> argumentTypes, Object target, EvaluationContext evaluationContext) throws SpelEvaluationException {
      AccessException accessException = null;

      for (MethodResolver methodResolver : evaluationContext.getMethodResolvers()) {
         try {
            MethodExecutor methodExecutor = methodResolver.resolve(evaluationContext, target, this.name, argumentTypes);
            if (methodExecutor != null) {
               return new MethodReference.MethodExecutorSearchResult(methodExecutor, null);
            }
         } catch (AccessException ex) {
            accessException = ex;
            break;
         }
      }

      return new MethodReference.MethodExecutorSearchResult(null, accessException);
   }

   private void throwSimpleExceptionIfPossible(Object target, AccessException ex) {
      if (ex.getCause() instanceof InvocationTargetException cause) {
         Throwable rootCause = cause.getCause();
         if (rootCause instanceof RuntimeException runtimeException) {
            throw runtimeException;
         } else {
            throw new ExpressionInvocationTargetException(
               this.getStartPosition(),
               "A problem occurred when trying to execute method '" + this.name + "' on object of type [" + target.getClass().getName() + "]",
               rootCause
            );
         }
      }
   }

   private void updateExitTypeDescriptor() {
      MethodReference.CachedMethodExecutor executorToCheck = this.cachedExecutor;
      if (executorToCheck != null && executorToCheck.get() instanceof ReflectiveMethodExecutor reflectiveMethodExecutor) {
         Method method = reflectiveMethodExecutor.getMethod();
         String descriptor = CodeFlow.toDescriptor(method.getReturnType());
         if (this.isNullSafe() && CodeFlow.isPrimitive(descriptor) && descriptor.charAt(0) != 'V') {
            this.originalPrimitiveExitTypeDescriptor = descriptor.charAt(0);
            this.exitTypeDescriptor = CodeFlow.toBoxedDescriptor(descriptor);
         } else {
            this.exitTypeDescriptor = descriptor;
         }
      }
   }

   @Override
   public String toStringAST() {
      StringJoiner sj = new StringJoiner(",", "(", ")");

      for (int i = 0; i < this.getChildCount(); i++) {
         sj.add(this.getChild(i).toStringAST());
      }

      return this.name + sj;
   }

   @Override
   public boolean isCompilable() {
      MethodReference.CachedMethodExecutor executorToCheck = this.cachedExecutor;
      if (executorToCheck != null && !executorToCheck.hasProxyTarget() && executorToCheck.get() instanceof ReflectiveMethodExecutor executor) {
         for (SpelNodeImpl child : this.children) {
            if (!child.isCompilable()) {
               return false;
            }
         }

         if (executor.didArgumentConversionOccur()) {
            return false;
         }

         Method method = executor.getMethod();
         return Modifier.isPublic(method.getModifiers()) && executor.getPublicDeclaringClass() != null;
      } else {
         return false;
      }
   }

   @Override
   public void generateCode(MethodVisitor mv, CodeFlow cf) {
      MethodReference.CachedMethodExecutor executorToCheck = this.cachedExecutor;
      if (executorToCheck != null && executorToCheck.get() instanceof ReflectiveMethodExecutor methodExecutor) {
         Method var13 = methodExecutor.getMethod();
         Class<?> publicDeclaringClass = methodExecutor.getPublicDeclaringClass();
         Assert.state(publicDeclaringClass != null, () -> "Failed to find public declaring class for method: " + var13);
         String classDesc = publicDeclaringClass.getName().replace('.', '/');
         boolean isStatic = Modifier.isStatic(var13.getModifiers());
         String descriptor = cf.lastDescriptor();
         if (descriptor == null && !isStatic) {
            cf.loadTarget(mv);
         }

         Label skipIfNull = null;
         if (this.isNullSafe() && (descriptor != null || !isStatic)) {
            if (this.unwrapOptional) {
               CodeFlow.insertOptionalUnwrapIfNecessary(mv, descriptor);
            }

            skipIfNull = new Label();
            Label continueLabel = new Label();
            mv.visitInsn(89);
            mv.visitJumpInsn(199, continueLabel);
            CodeFlow.insertCheckCast(mv, this.exitTypeDescriptor);
            mv.visitJumpInsn(167, skipIfNull);
            mv.visitLabel(continueLabel);
         }

         if (descriptor != null && isStatic) {
            mv.visitInsn(87);
         }

         if (CodeFlow.isPrimitive(descriptor)) {
            CodeFlow.insertBoxIfNecessary(mv, descriptor.charAt(0));
         }

         if (!isStatic && (descriptor == null || !descriptor.substring(1).equals(classDesc))) {
            CodeFlow.insertCheckCast(mv, "L" + classDesc);
         }

         generateCodeForArguments(mv, cf, var13, this.children);
         boolean isInterface = publicDeclaringClass.isInterface();
         int opcode = isStatic ? 184 : (isInterface ? 185 : 182);
         mv.visitMethodInsn(opcode, classDesc, var13.getName(), CodeFlow.createSignatureDescriptor(var13), isInterface);
         cf.pushDescriptor(this.exitTypeDescriptor);
         if (this.originalPrimitiveExitTypeDescriptor != null) {
            CodeFlow.insertBoxIfNecessary(mv, this.originalPrimitiveExitTypeDescriptor);
         }

         if (skipIfNull != null) {
            if ("V".equals(this.exitTypeDescriptor)) {
               mv.visitInsn(1);
            }

            mv.visitLabel(skipIfNull);
         }
      } else {
         throw new IllegalStateException("No applicable cached executor found: " + executorToCheck);
      }
   }

   private record CachedMethodExecutor(
      MethodExecutor methodExecutor, @Nullable Class<?> staticClass, @Nullable TypeDescriptor targetType, List<TypeDescriptor> argumentTypes
   ) {
      public boolean isSuitable(Object target, @Nullable TypeDescriptor targetType, List<TypeDescriptor> argumentTypes) {
         return (this.staticClass == null || this.staticClass == target)
            && Objects.equals(this.targetType, targetType)
            && this.argumentTypes.equals(argumentTypes);
      }

      public boolean hasProxyTarget() {
         return this.targetType != null && Proxy.isProxyClass(this.targetType.getType());
      }

      public MethodExecutor get() {
         return this.methodExecutor;
      }
   }

   private record MethodExecutorSearchResult(@Nullable MethodExecutor methodExecutor, @Nullable AccessException accessException) {
   }

   private class MethodValueRef implements ValueRef {
      private final ExpressionState expressionState;
      private final EvaluationContext evaluationContext;
      private final @Nullable Object target;
      private final @Nullable TypeDescriptor targetType;
      private final @Nullable Object[] arguments;

      public MethodValueRef(ExpressionState state, @Nullable Object[] arguments) {
         this.expressionState = state;
         this.evaluationContext = state.getEvaluationContext();
         this.target = state.getActiveContextObject().getValue();
         this.targetType = state.getActiveContextObject().getTypeDescriptor();
         this.arguments = arguments;
      }

      @Override
      public TypedValue getValue() {
         TypedValue result = MethodReference.this.getValueInternal(this.expressionState, this.evaluationContext, this.target, this.targetType, this.arguments);
         MethodReference.this.updateExitTypeDescriptor();
         return result;
      }

      @Override
      public void setValue(@Nullable Object newValue) {
         throw new IllegalAccessError();
      }

      @Override
      public boolean isWritable() {
         return false;
      }
   }
}
