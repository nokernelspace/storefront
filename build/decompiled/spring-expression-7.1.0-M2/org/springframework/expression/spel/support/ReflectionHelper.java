package org.springframework.expression.spel.support;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.TypeConverter;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

public abstract class ReflectionHelper {
   static ReflectionHelper.@Nullable ArgumentsMatchKind compareArguments(
      List<TypeDescriptor> expectedArgTypes, List<TypeDescriptor> suppliedArgTypes, TypeConverter typeConverter
   ) {
      Assert.isTrue(expectedArgTypes.size() == suppliedArgTypes.size(), "Expected argument types and supplied argument types should be lists of the same size");
      ReflectionHelper.ArgumentsMatchKind match = ReflectionHelper.ArgumentsMatchKind.EXACT;

      for (int i = 0; i < expectedArgTypes.size() && match != null; i++) {
         TypeDescriptor suppliedArg = suppliedArgTypes.get(i);
         TypeDescriptor expectedArg = expectedArgTypes.get(i);
         if (suppliedArg == null) {
            if (expectedArg.isPrimitive()) {
               match = null;
            }
         } else if (!expectedArg.equals(suppliedArg)) {
            if (suppliedArg.isAssignableTo(expectedArg)) {
               if (match != ReflectionHelper.ArgumentsMatchKind.REQUIRES_CONVERSION) {
                  match = ReflectionHelper.ArgumentsMatchKind.CLOSE;
               }
            } else if (typeConverter.canConvert(suppliedArg, expectedArg)) {
               match = ReflectionHelper.ArgumentsMatchKind.REQUIRES_CONVERSION;
            } else {
               match = null;
            }
         }
      }

      return match;
   }

   public static int getTypeDifferenceWeight(List<TypeDescriptor> paramTypes, List<TypeDescriptor> argTypes) {
      int result = 0;

      for (int i = 0; i < paramTypes.size(); i++) {
         TypeDescriptor paramType = paramTypes.get(i);
         TypeDescriptor argType = i < argTypes.size() ? argTypes.get(i) : null;
         if (argType == null) {
            if (paramType.isPrimitive()) {
               return Integer.MAX_VALUE;
            }
         } else {
            Class<?> paramTypeClazz = paramType.getType();
            if (!ClassUtils.isAssignable(paramTypeClazz, argType.getType())) {
               return Integer.MAX_VALUE;
            }

            if (paramTypeClazz.isPrimitive()) {
               paramTypeClazz = Object.class;
            }

            Class<?> superClass = argType.getType().getSuperclass();

            while (superClass != null) {
               if (paramTypeClazz.equals(superClass)) {
                  result += 2;
                  superClass = null;
               } else if (ClassUtils.isAssignable(paramTypeClazz, superClass)) {
                  result += 2;
                  superClass = superClass.getSuperclass();
               } else {
                  superClass = null;
               }
            }

            if (paramTypeClazz.isInterface()) {
               result++;
            }
         }
      }

      return result;
   }

   static ReflectionHelper.@Nullable ArgumentsMatchKind compareArgumentsVarargs(
      List<TypeDescriptor> expectedArgTypes, List<TypeDescriptor> suppliedArgTypes, TypeConverter typeConverter
   ) {
      Assert.isTrue(!CollectionUtils.isEmpty(expectedArgTypes), "Expected arguments must at least include one array (the varargs parameter)");
      Assert.isTrue(expectedArgTypes.get(expectedArgTypes.size() - 1).isArray(), "Final expected argument should be array type (the varargs parameter)");
      ReflectionHelper.ArgumentsMatchKind match = ReflectionHelper.ArgumentsMatchKind.EXACT;
      int argCountUpToVarargs = expectedArgTypes.size() - 1;

      for (int i = 0; i < argCountUpToVarargs && match != null; i++) {
         TypeDescriptor suppliedArg = suppliedArgTypes.get(i);
         TypeDescriptor expectedArg = expectedArgTypes.get(i);
         if (suppliedArg == null) {
            if (expectedArg.isPrimitive()) {
               match = null;
            }
         } else if (!expectedArg.equals(suppliedArg)) {
            if (suppliedArg.isAssignableTo(expectedArg)) {
               if (match != ReflectionHelper.ArgumentsMatchKind.REQUIRES_CONVERSION) {
                  match = ReflectionHelper.ArgumentsMatchKind.CLOSE;
               }
            } else if (typeConverter.canConvert(suppliedArg, expectedArg)) {
               match = ReflectionHelper.ArgumentsMatchKind.REQUIRES_CONVERSION;
            } else {
               match = null;
            }
         }
      }

      if (match == null) {
         return null;
      }

      if (suppliedArgTypes.size() != expectedArgTypes.size()
         || !expectedArgTypes.get(expectedArgTypes.size() - 1).equals(suppliedArgTypes.get(suppliedArgTypes.size() - 1))) {
         TypeDescriptor varargsDesc = expectedArgTypes.get(expectedArgTypes.size() - 1);
         TypeDescriptor componentTypeDesc = varargsDesc.getElementTypeDescriptor();
         Assert.state(componentTypeDesc != null, "Component type must not be null for a varargs array");
         Class<?> varargsComponentType = componentTypeDesc.getType();

         for (int i = expectedArgTypes.size() - 1; i < suppliedArgTypes.size(); i++) {
            TypeDescriptor suppliedArg = suppliedArgTypes.get(i);
            if (suppliedArg == null) {
               if (varargsComponentType.isPrimitive()) {
                  match = null;
               }
            } else if (varargsComponentType != suppliedArg.getType()) {
               if (ClassUtils.isAssignable(varargsComponentType, suppliedArg.getType())) {
                  if (match != ReflectionHelper.ArgumentsMatchKind.REQUIRES_CONVERSION) {
                     match = ReflectionHelper.ArgumentsMatchKind.CLOSE;
                  }
               } else if (typeConverter.canConvert(suppliedArg, TypeDescriptor.valueOf(varargsComponentType))) {
                  match = ReflectionHelper.ArgumentsMatchKind.REQUIRES_CONVERSION;
               } else {
                  match = null;
               }
            }
         }
      }

      return match;
   }

   public static boolean convertAllArguments(TypeConverter converter, @Nullable Object[] arguments, Method method) throws SpelEvaluationException {
      Integer varargsPosition = method.isVarArgs() ? method.getParameterCount() - 1 : null;
      return convertArguments(converter, arguments, method, varargsPosition);
   }

   static boolean convertArguments(TypeConverter converter, @Nullable Object[] arguments, Executable executable, @Nullable Integer varargsPosition) throws EvaluationException {
      boolean conversionOccurred = false;
      if (varargsPosition == null) {
         for (int i = 0; i < arguments.length; i++) {
            TypeDescriptor targetType = new TypeDescriptor(MethodParameter.forExecutable(executable, i));
            Object argument = arguments[i];
            TypeDescriptor sourceType = TypeDescriptor.forObject(argument);
            arguments[i] = converter.convertValue(argument, sourceType, targetType);
            conversionOccurred |= argument != arguments[i];
         }
      } else {
         for (int i = 0; i < varargsPosition; i++) {
            TypeDescriptor targetType = new TypeDescriptor(MethodParameter.forExecutable(executable, i));
            Object argument = arguments[i];
            TypeDescriptor sourceType = TypeDescriptor.forObject(argument);
            arguments[i] = converter.convertValue(argument, sourceType, targetType);
            conversionOccurred |= argument != arguments[i];
         }

         MethodParameter methodParam = MethodParameter.forExecutable(executable, varargsPosition);
         TypeDescriptor targetType = new TypeDescriptor(methodParam);
         TypeDescriptor componentTypeDesc = targetType.getElementTypeDescriptor();
         Assert.state(componentTypeDesc != null, "Component type must not be null for a varargs array");
         if (varargsPosition == arguments.length - 1) {
            Object argument = arguments[varargsPosition];
            TypeDescriptor sourceType = TypeDescriptor.forObject(argument);
            if (argument == null) {
               if (componentTypeDesc.getObjectType() == Optional.class) {
                  arguments[varargsPosition] = Optional.empty();
                  conversionOccurred = true;
               }
            } else if (!sourceType.isAssignableTo(componentTypeDesc)
               || sourceType.isArray() && !sourceType.isAssignableTo(targetType)
               || argument instanceof List) {
               TypeDescriptor targetTypeToUse = !sourceType.isArray() && !(argument instanceof List) && !converter.canConvert(sourceType, targetType)
                  ? componentTypeDesc
                  : targetType;
               arguments[varargsPosition] = converter.convertValue(argument, sourceType, targetTypeToUse);
            }

            if (argument != arguments[varargsPosition] && !isFirstEntryInArray(argument, arguments[varargsPosition])) {
               conversionOccurred = true;
            }
         } else {
            for (int i = varargsPosition; i < arguments.length; i++) {
               Object argument = arguments[i];
               TypeDescriptor sourceType = TypeDescriptor.forObject(argument);
               arguments[i] = converter.convertValue(argument, sourceType, componentTypeDesc);
               conversionOccurred |= argument != arguments[i];
            }
         }
      }

      return conversionOccurred;
   }

   public static boolean convertAllMethodHandleArguments(
      TypeConverter converter, @Nullable Object[] arguments, MethodHandle methodHandle, @Nullable Integer varargsPosition
   ) throws EvaluationException {
      boolean conversionOccurred = false;
      MethodType methodHandleType = methodHandle.type();
      if (varargsPosition == null) {
         for (int i = 0; i < arguments.length; i++) {
            Class<?> argumentClass = methodHandleType.parameterType(i);
            ResolvableType resolvableType = ResolvableType.forClass(argumentClass);
            TypeDescriptor targetType = new TypeDescriptor(resolvableType, argumentClass, null);
            Object argument = arguments[i];
            TypeDescriptor sourceType = TypeDescriptor.forObject(argument);
            arguments[i] = converter.convertValue(argument, sourceType, targetType);
            conversionOccurred |= argument != arguments[i];
         }
      } else {
         for (int i = 0; i < varargsPosition; i++) {
            Class<?> argumentClass = methodHandleType.parameterType(i);
            ResolvableType resolvableType = ResolvableType.forClass(argumentClass);
            TypeDescriptor targetType = new TypeDescriptor(resolvableType, argumentClass, null);
            Object argument = arguments[i];
            TypeDescriptor sourceType = TypeDescriptor.forObject(argument);
            arguments[i] = converter.convertValue(argument, sourceType, targetType);
            conversionOccurred |= argument != arguments[i];
         }

         Class<?> varargsArrayClass = methodHandleType.lastParameterType();
         Class<?> varargsComponentClass = ClassUtils.resolvePrimitiveIfNecessary(varargsArrayClass.componentType());
         TypeDescriptor varargsArrayType = TypeDescriptor.array(TypeDescriptor.valueOf(varargsComponentClass));
         TypeDescriptor varargsComponentType = varargsArrayType.getElementTypeDescriptor();
         Assert.state(varargsComponentType != null, "Component type must not be null for a varargs array");
         if (varargsPosition == arguments.length - 1) {
            Object argument = arguments[varargsPosition];
            TypeDescriptor sourceType = TypeDescriptor.forObject(argument);
            if (argument == null) {
               if (varargsComponentType.getObjectType() == Optional.class) {
                  arguments[varargsPosition] = Optional.empty();
                  conversionOccurred = true;
               }
            } else if (!sourceType.isAssignableTo(varargsComponentType)
               || sourceType.isArray() && !sourceType.isAssignableTo(varargsArrayType)
               || argument instanceof List) {
               TypeDescriptor targetTypeToUse = !sourceType.isArray() && !(argument instanceof List) && !converter.canConvert(sourceType, varargsArrayType)
                  ? varargsComponentType
                  : varargsArrayType;
               arguments[varargsPosition] = converter.convertValue(argument, sourceType, targetTypeToUse);
            }

            if (argument != arguments[varargsPosition] && !isFirstEntryInArray(argument, arguments[varargsPosition])) {
               conversionOccurred = true;
            }
         } else {
            for (int i = varargsPosition; i < arguments.length; i++) {
               Object argument = arguments[i];
               TypeDescriptor sourceType = TypeDescriptor.forObject(argument);
               arguments[i] = converter.convertValue(argument, sourceType, varargsComponentType);
               conversionOccurred |= argument != arguments[i];
            }
         }
      }

      return conversionOccurred;
   }

   private static boolean isFirstEntryInArray(@Nullable Object value, @Nullable Object possibleArray) {
      if (possibleArray == null) {
         return false;
      } else {
         Class<?> type = possibleArray.getClass();
         if (type.isArray() && Array.getLength(possibleArray) != 0 && ClassUtils.isAssignableValue(type.componentType(), value)) {
            Object arrayValue = Array.get(possibleArray, 0);
            return type.componentType().isPrimitive() ? arrayValue.equals(value) : arrayValue == value;
         } else {
            return false;
         }
      }
   }

   public static Object[] setupArgumentsForVarargsInvocation(Class<?>[] requiredParameterTypes, Object... args) {
      Assert.notEmpty(requiredParameterTypes, "Required parameter types array must not be empty");
      int parameterCount = requiredParameterTypes.length;
      Class<?> lastRequiredParameterType = requiredParameterTypes[parameterCount - 1];
      Assert.isTrue(lastRequiredParameterType.isArray(), "The last required parameter type must be an array to support varargs invocation");
      int argumentCount = args.length;
      Object lastArgument = argumentCount > 0 ? args[argumentCount - 1] : null;
      if (parameterCount == argumentCount && lastRequiredParameterType.isInstance(lastArgument)) {
         return args;
      }

      Object[] newArgs = new Object[parameterCount];
      System.arraycopy(args, 0, newArgs, 0, newArgs.length - 1);
      int varargsArraySize = 0;
      if (argumentCount >= parameterCount) {
         varargsArraySize = argumentCount - (parameterCount - 1);
      }

      Class<?> componentType = lastRequiredParameterType.componentType();
      Object varargsArray = Array.newInstance(componentType, varargsArraySize);

      for (int i = 0; i < varargsArraySize; i++) {
         Array.set(varargsArray, i, args[parameterCount - 1 + i]);
      }

      newArgs[newArgs.length - 1] = varargsArray;
      return newArgs;
   }

   enum ArgumentsMatchKind {
      EXACT,
      CLOSE,
      REQUIRES_CONVERSION;

      public boolean isExactMatch() {
         return this == EXACT;
      }

      public boolean isCloseMatch() {
         return this == CLOSE;
      }

      public boolean isMatchRequiringConversion() {
         return this == REQUIRES_CONVERSION;
      }
   }
}
