package org.springframework.core;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ReflectionUtils;

public final class BridgeMethodResolver {
   private static final Map<Object, Method> cache = new ConcurrentReferenceHashMap<>();

   private BridgeMethodResolver() {
   }

   public static Method findBridgedMethod(Method bridgeMethod) {
      return resolveBridgeMethod(bridgeMethod, bridgeMethod.getDeclaringClass());
   }

   public static Method getMostSpecificMethod(Method bridgeMethod, @Nullable Class<?> targetClass) {
      if (targetClass != null
         && !ClassUtils.getUserClass(bridgeMethod.getDeclaringClass()).isAssignableFrom(targetClass)
         && !Proxy.isProxyClass(bridgeMethod.getDeclaringClass())) {
         return bridgeMethod;
      }

      Method specificMethod = ClassUtils.getMostSpecificMethod(bridgeMethod, targetClass);
      return resolveBridgeMethod(specificMethod, targetClass != null ? targetClass : specificMethod.getDeclaringClass());
   }

   private static Method resolveBridgeMethod(Method bridgeMethod, Class<?> targetClass) {
      Class<?> userClass = targetClass;
      if (!bridgeMethod.isBridge()) {
         userClass = ClassUtils.getUserClass(targetClass);
         if (userClass == targetClass) {
            return bridgeMethod;
         }
      }

      boolean localBridge = targetClass == bridgeMethod.getDeclaringClass();
      Object cacheKey = localBridge ? bridgeMethod : new MethodClassKey(bridgeMethod, targetClass);
      Method bridgedMethod = cache.get(cacheKey);
      if (bridgedMethod == null) {
         List<Method> candidateMethods = new ArrayList<>();
         ReflectionUtils.doWithMethods(userClass, candidateMethods::add, candidateMethod -> isBridgedCandidateFor(candidateMethod, bridgeMethod));
         if (!candidateMethods.isEmpty()) {
            bridgedMethod = candidateMethods.size() == 1 ? candidateMethods.get(0) : searchCandidates(candidateMethods, bridgeMethod, targetClass);
         }

         if (bridgedMethod == null) {
            bridgedMethod = bridgeMethod;
         }

         cache.put(cacheKey, bridgedMethod);
      }

      return bridgedMethod;
   }

   private static boolean isBridgedCandidateFor(Method candidateMethod, Method bridgeMethod) {
      return !candidateMethod.isBridge()
         && candidateMethod.getName().equals(bridgeMethod.getName())
         && candidateMethod.getParameterCount() == bridgeMethod.getParameterCount();
   }

   private static @Nullable Method searchCandidates(List<Method> candidateMethods, Method bridgeMethod, Class<?> targetClass) {
      Method previousMethod = null;
      boolean sameSig = true;

      for (Method candidateMethod : candidateMethods) {
         if (isBridgeMethodFor(bridgeMethod, candidateMethod, targetClass)) {
            return candidateMethod;
         }

         if (previousMethod != null) {
            sameSig = sameSig && Arrays.equals(candidateMethod.getGenericParameterTypes(), previousMethod.getGenericParameterTypes());
         }

         previousMethod = candidateMethod;
      }

      return sameSig ? candidateMethods.get(0) : null;
   }

   static boolean isBridgeMethodFor(Method bridgeMethod, Method candidateMethod, Class<?> targetClass) {
      if (isResolvedTypeMatch(candidateMethod, bridgeMethod, targetClass)) {
         return true;
      }

      Method method = findGenericDeclaration(bridgeMethod);
      return method != null && isResolvedTypeMatch(method, candidateMethod, targetClass);
   }

   private static boolean isResolvedTypeMatch(Method genericMethod, Method candidateMethod, Class<?> targetClass) {
      Type[] genericParameters = genericMethod.getGenericParameterTypes();
      if (genericParameters.length != candidateMethod.getParameterCount()) {
         return false;
      }

      for (Class<?> clazz = targetClass; clazz != null && clazz != Object.class && clazz != genericMethod.getDeclaringClass(); clazz = clazz.getSuperclass()) {
         if (checkResolvedTypeMatch(genericMethod, candidateMethod, clazz)) {
            return true;
         }
      }

      return false;
   }

   private static boolean checkResolvedTypeMatch(Method genericMethod, Method candidateMethod, Class<?> clazz) {
      ResolvableType genericReturnType = ResolvableType.forMethodReturnType(genericMethod, clazz);
      if (!ClassUtils.resolvePrimitiveIfNecessary(genericReturnType.toClass())
         .isAssignableFrom(ClassUtils.resolvePrimitiveIfNecessary(candidateMethod.getReturnType()))) {
         return false;
      }

      Class<?>[] candidateParameters = candidateMethod.getParameterTypes();

      for (int i = 0; i < candidateParameters.length; i++) {
         ResolvableType genericParameter = ResolvableType.forMethodParameter(genericMethod, i, clazz);
         if (!ClassUtils.resolvePrimitiveIfNecessary(genericParameter.toClass()).equals(ClassUtils.resolvePrimitiveIfNecessary(candidateParameters[i]))) {
            return false;
         }
      }

      return true;
   }

   private static @Nullable Method findGenericDeclaration(Method bridgeMethod) {
      if (!bridgeMethod.isBridge()) {
         return bridgeMethod;
      }

      for (Class<?> superclass = bridgeMethod.getDeclaringClass().getSuperclass();
         superclass != null && Object.class != superclass;
         superclass = superclass.getSuperclass()
      ) {
         Method method = searchForMatch(superclass, bridgeMethod);
         if (method != null && !method.isBridge()) {
            return method;
         }
      }

      Class<?>[] interfaces = ClassUtils.getAllInterfacesForClass(bridgeMethod.getDeclaringClass());
      return searchInterfaces(interfaces, bridgeMethod);
   }

   private static @Nullable Method searchInterfaces(Class<?>[] interfaces, Method bridgeMethod) {
      for (Class<?> ifc : interfaces) {
         Method method = searchForMatch(ifc, bridgeMethod);
         if (method != null && !method.isBridge()) {
            return method;
         }

         method = searchInterfaces(ifc.getInterfaces(), bridgeMethod);
         if (method != null) {
            return method;
         }
      }

      return null;
   }

   private static @Nullable Method searchForMatch(Class<?> type, Method bridgeMethod) {
      try {
         return type.getDeclaredMethod(bridgeMethod.getName(), bridgeMethod.getParameterTypes());
      } catch (NoSuchMethodException ex) {
         return null;
      }
   }

   @Deprecated(since = "6.2.13", forRemoval = true)
   public static boolean isVisibilityBridgeMethodPair(Method bridgeMethod, Method bridgedMethod) {
      if (bridgeMethod == bridgedMethod) {
         return true;
      } else {
         return ClassUtils.getUserClass(bridgeMethod.getDeclaringClass()) != bridgeMethod.getDeclaringClass()
            ? false
            : bridgeMethod.getReturnType().equals(bridgedMethod.getReturnType())
               && bridgeMethod.getParameterCount() == bridgedMethod.getParameterCount()
               && Arrays.equals(bridgeMethod.getParameterTypes(), bridgedMethod.getParameterTypes());
      }
   }
}
