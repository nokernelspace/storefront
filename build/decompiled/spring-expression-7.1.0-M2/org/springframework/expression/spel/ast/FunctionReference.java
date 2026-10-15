package org.springframework.expression.spel.ast;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.StringJoiner;
import org.jspecify.annotations.Nullable;
import org.springframework.asm.MethodVisitor;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.TypeConverter;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.CodeFlow;
import org.springframework.expression.spel.ExpressionState;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.expression.spel.support.ReflectionHelper;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

public class FunctionReference extends SpelNodeImpl {
   private final String name;
   private volatile @Nullable Method method;

   public FunctionReference(String functionName, int startPos, int endPos, SpelNodeImpl... arguments) {
      super(startPos, endPos, arguments);
      this.name = functionName;
   }

   @Override
   public TypedValue getValueInternal(ExpressionState state) throws EvaluationException {
      TypedValue value = state.lookupVariable(this.name);
      if (value == TypedValue.NULL) {
         throw new SpelEvaluationException(this.getStartPosition(), SpelMessage.FUNCTION_NOT_DEFINED, this.name);
      }

      Object function = value.getValue();
      if (function instanceof Method javaMethod) {
         try {
            state.trackOperation();
            return this.executeFunctionViaMethod(state, javaMethod);
         } catch (SpelEvaluationException ex) {
            ex.setPosition(this.getStartPosition());
            throw ex;
         }
      } else if (function instanceof MethodHandle methodHandle) {
         try {
            state.trackOperation();
            return this.executeFunctionViaMethodHandle(state, methodHandle);
         } catch (SpelEvaluationException ex) {
            ex.setPosition(this.getStartPosition());
            throw ex;
         }
      } else {
         throw new SpelEvaluationException(SpelMessage.FUNCTION_REFERENCE_CANNOT_BE_INVOKED, this.name, value.getClass());
      }
   }

   private TypedValue executeFunctionViaMethod(ExpressionState state, Method method) throws EvaluationException {
      Object[] functionArgs = this.getArguments(state);
      if (!method.isVarArgs()) {
         int declaredParamCount = method.getParameterCount();
         if (declaredParamCount != functionArgs.length) {
            throw new SpelEvaluationException(SpelMessage.INCORRECT_NUMBER_OF_ARGUMENTS_TO_FUNCTION, this.name, functionArgs.length, declaredParamCount);
         }
      }

      if (!Modifier.isStatic(method.getModifiers())) {
         throw new SpelEvaluationException(this.getStartPosition(), SpelMessage.FUNCTION_MUST_BE_STATIC, ClassUtils.getQualifiedMethodName(method), this.name);
      }

      TypeConverter converter = state.getEvaluationContext().getTypeConverter();
      boolean argumentConversionOccurred = ReflectionHelper.convertAllArguments(converter, functionArgs, method);
      if (method.isVarArgs()) {
         functionArgs = ReflectionHelper.setupArgumentsForVarargsInvocation(method.getParameterTypes(), functionArgs);
      }

      boolean compilable = false;

      try {
         ReflectionUtils.makeAccessible(method);
         Object result = method.invoke(method.getClass(), functionArgs);
         compilable = !argumentConversionOccurred;
         return new TypedValue(result, new TypeDescriptor(new MethodParameter(method, -1)).narrow(result));
      } catch (Exception ex) {
         Throwable cause = ex instanceof InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : ex;
         throw new SpelEvaluationException(this.getStartPosition(), cause, SpelMessage.EXCEPTION_DURING_FUNCTION_CALL, this.name, cause.getMessage());
      } finally {
         if (compilable) {
            this.exitTypeDescriptor = CodeFlow.toDescriptor(method.getReturnType());
            this.method = method;
         } else {
            this.exitTypeDescriptor = null;
            this.method = null;
         }
      }
   }

   private TypedValue executeFunctionViaMethodHandle(ExpressionState state, MethodHandle methodHandle) throws EvaluationException {
      Object[] functionArgs = this.getArguments(state);
      MethodType declaredParams = methodHandle.type();
      int spelParamCount = functionArgs.length;
      int declaredParamCount = declaredParams.parameterCount();
      boolean isSuspectedVarargs = declaredParams.lastParameterType().isArray();
      if (isSuspectedVarargs) {
         if (spelParamCount < declaredParamCount - 1) {
            throw new SpelEvaluationException(
               SpelMessage.INCORRECT_NUMBER_OF_ARGUMENTS_TO_FUNCTION, this.name, spelParamCount, declaredParamCount - 1 + " or more"
            );
         }
      } else if (spelParamCount != declaredParamCount) {
         throw new SpelEvaluationException(SpelMessage.INCORRECT_NUMBER_OF_ARGUMENTS_TO_FUNCTION, this.name, spelParamCount, declaredParamCount);
      }

      if (declaredParamCount == 0) {
         try {
            return new TypedValue((Object)methodHandle.invoke());
         } catch (Throwable ex) {
            throw new SpelEvaluationException(this.getStartPosition(), ex, SpelMessage.EXCEPTION_DURING_FUNCTION_CALL, this.name, ex.getMessage());
         } finally {
            this.exitTypeDescriptor = null;
            this.method = null;
         }
      } else {
         Integer varArgPosition = null;
         if (isSuspectedVarargs) {
            varArgPosition = declaredParamCount - 1;
         }

         TypeConverter converter = state.getEvaluationContext().getTypeConverter();
         ReflectionHelper.convertAllMethodHandleArguments(converter, functionArgs, methodHandle, varArgPosition);
         if (isSuspectedVarargs) {
            if (declaredParamCount == 1 && !methodHandle.isVarargsCollector()) {
               functionArgs = ReflectionHelper.setupArgumentsForVarargsInvocation(methodHandle.type().parameterArray(), functionArgs);
            } else if (spelParamCount == declaredParamCount) {
               int actualVarargsIndex = functionArgs.length - 1;
               if (actualVarargsIndex >= 0 && functionArgs[actualVarargsIndex] instanceof Object[] argsToUnpack) {
                  Object[] newArgs = new Object[actualVarargsIndex + argsToUnpack.length];
                  System.arraycopy(functionArgs, 0, newArgs, 0, actualVarargsIndex);
                  System.arraycopy(argsToUnpack, 0, newArgs, actualVarargsIndex, argsToUnpack.length);
                  functionArgs = newArgs;
               }
            }
         }

         try {
            return new TypedValue(methodHandle.invokeWithArguments(functionArgs));
         } catch (Throwable ex) {
            throw new SpelEvaluationException(this.getStartPosition(), ex, SpelMessage.EXCEPTION_DURING_FUNCTION_CALL, this.name, ex.getMessage());
         } finally {
            this.exitTypeDescriptor = null;
            this.method = null;
         }
      }
   }

   @Override
   public String toStringAST() {
      StringJoiner sj = new StringJoiner(",", "(", ")");

      for (int i = 0; i < this.getChildCount(); i++) {
         sj.add(this.getChild(i).toStringAST());
      }

      return "#" + this.name + sj;
   }

   private Object[] getArguments(ExpressionState state) throws EvaluationException {
      Object[] arguments = new Object[this.getChildCount()];

      for (int i = 0; i < arguments.length; i++) {
         arguments[i] = this.children[i].getValueInternal(state).getValue();
      }

      return arguments;
   }

   @Override
   public boolean isCompilable() {
      Method method = this.method;
      if (method == null) {
         return false;
      }

      int methodModifiers = method.getModifiers();
      if (Modifier.isStatic(methodModifiers) && Modifier.isPublic(methodModifiers) && Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
         for (SpelNodeImpl child : this.children) {
            if (!child.isCompilable()) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   @Override
   public void generateCode(MethodVisitor mv, CodeFlow cf) {
      Method method = this.method;
      Assert.state(method != null, "No method handle");
      String classDesc = method.getDeclaringClass().getName().replace('.', '/');
      generateCodeForArguments(mv, cf, method, this.children);
      mv.visitMethodInsn(184, classDesc, method.getName(), CodeFlow.createSignatureDescriptor(method), false);
      cf.pushDescriptor(this.exitTypeDescriptor);
   }
}
