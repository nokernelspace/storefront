package org.springframework.util;

import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.jspecify.annotations.Nullable;

public class MethodInvoker {
   private static final Object[] EMPTY_ARGUMENTS = new Object[0];
   protected @Nullable Class<?> targetClass;
   private @Nullable Object targetObject;
   private @Nullable String targetMethod;
   private @Nullable String staticMethod;
   private @Nullable Object @Nullable [] arguments;
   private @Nullable Method methodObject;

   public void setTargetClass(@Nullable Class<?> targetClass) {
      this.targetClass = targetClass;
   }

   public @Nullable Class<?> getTargetClass() {
      return this.targetClass;
   }

   public void setTargetObject(@Nullable Object targetObject) {
      this.targetObject = targetObject;
      if (targetObject != null) {
         this.targetClass = targetObject.getClass();
      }
   }

   public @Nullable Object getTargetObject() {
      return this.targetObject;
   }

   public void setTargetMethod(@Nullable String targetMethod) {
      this.targetMethod = targetMethod;
   }

   public @Nullable String getTargetMethod() {
      return this.targetMethod;
   }

   public void setStaticMethod(String staticMethod) {
      this.staticMethod = staticMethod;
   }

   public void setArguments(@Nullable Object @Nullable ... arguments) {
      this.arguments = arguments;
   }

   public @Nullable Object[] getArguments() {
      return this.arguments != null ? this.arguments : EMPTY_ARGUMENTS;
   }

   public void prepare() throws ClassNotFoundException, NoSuchMethodException {
      if (this.staticMethod != null) {
         int lastDotIndex = this.staticMethod.lastIndexOf(46);
         if (lastDotIndex == -1 || lastDotIndex == this.staticMethod.length() - 1) {
            throw new IllegalArgumentException(
               "staticMethod must be a fully qualified class plus method name: for example, 'example.MyExampleClass.myExampleMethod'"
            );
         }

         String className = this.staticMethod.substring(0, lastDotIndex);
         String methodName = this.staticMethod.substring(lastDotIndex + 1);
         if (this.targetClass == null || !this.targetClass.getName().equals(className)) {
            this.targetClass = this.resolveClassName(className);
         }

         this.targetMethod = methodName;
      }

      Class<?> targetClass = this.getTargetClass();
      String targetMethod = this.getTargetMethod();
      Assert.notNull(targetClass, "Either 'targetClass' or 'targetObject' is required");
      Assert.notNull(targetMethod, "Property 'targetMethod' is required");
      Object[] arguments = this.getArguments();
      Class<?>[] argTypes = new Class[arguments.length];

      for (int i = 0; i < arguments.length; i++) {
         Object argument = arguments[i];
         argTypes[i] = argument != null ? argument.getClass() : Object.class;
      }

      try {
         this.methodObject = targetClass.getMethod(targetMethod, argTypes);
      } catch (NoSuchMethodException ex) {
         this.methodObject = this.findMatchingMethod();
         if (this.methodObject == null) {
            throw ex;
         }
      }
   }

   protected Class<?> resolveClassName(String className) throws ClassNotFoundException {
      return ClassUtils.forName(className, ClassUtils.getDefaultClassLoader());
   }

   protected Method findMatchingMethod() {
      String targetMethod = this.getTargetMethod();
      Object[] arguments = this.getArguments();
      int argCount = arguments.length;
      Class<?> targetClass = this.getTargetClass();
      Assert.state(targetClass != null, "No target class set");
      Method[] candidates = ReflectionUtils.getAllDeclaredMethods(targetClass);
      int minTypeDiffWeight = Integer.MAX_VALUE;
      Method matchingMethod = null;

      for (Method candidate : candidates) {
         if (candidate.getName().equals(targetMethod) && candidate.getParameterCount() == argCount) {
            Class<?>[] paramTypes = candidate.getParameterTypes();
            int typeDiffWeight = getTypeDifferenceWeight(paramTypes, arguments);
            if (typeDiffWeight < minTypeDiffWeight) {
               minTypeDiffWeight = typeDiffWeight;
               matchingMethod = candidate;
            }
         }
      }

      return matchingMethod;
   }

   public Method getPreparedMethod() throws IllegalStateException {
      if (this.methodObject == null) {
         throw new IllegalStateException("prepare() must be called prior to invoke() on MethodInvoker");
      } else {
         return this.methodObject;
      }
   }

   public boolean isPrepared() {
      return this.methodObject != null;
   }

   public @Nullable Object invoke() throws InvocationTargetException, IllegalAccessException {
      Object targetObject = this.getTargetObject();
      Method preparedMethod = this.getPreparedMethod();
      if (targetObject == null && !Modifier.isStatic(preparedMethod.getModifiers())) {
         throw new IllegalArgumentException("Target method must not be non-static without a target");
      }

      try {
         ReflectionUtils.makeAccessible(preparedMethod);
         return preparedMethod.invoke(targetObject, this.getArguments());
      } catch (IllegalAccessException | InaccessibleObjectException ex) {
         if (targetObject != null) {
            Method fallbackMethod = ClassUtils.getPubliclyAccessibleMethodIfPossible(preparedMethod, targetObject.getClass());
            if (fallbackMethod != preparedMethod) {
               return fallbackMethod.invoke(targetObject, this.getArguments());
            }
         }

         throw ex;
      }
   }

   public static int getTypeDifferenceWeight(Class<?>[] paramTypes, @Nullable Object[] args) {
      int result = 0;

      for (int i = 0; i < paramTypes.length; i++) {
         Class<?> paramType = paramTypes[i];
         Object arg = args[i];
         if (!ClassUtils.isAssignableValue(paramType, arg)) {
            return Integer.MAX_VALUE;
         }

         if (arg != null) {
            Class<?> superClass = arg.getClass().getSuperclass();

            while (superClass != null) {
               if (paramType.equals(superClass)) {
                  result += 2;
                  superClass = null;
               } else if (ClassUtils.isAssignable(paramType, superClass)) {
                  result += 2;
                  superClass = superClass.getSuperclass();
               } else {
                  superClass = null;
               }
            }

            if (paramType.isInterface()) {
               result++;
            }
         }
      }

      return result;
   }
}
