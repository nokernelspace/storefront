package org.springframework.beans.factory.support;

import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanMetadataElement;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

abstract class AutowireUtils {
   public static final Comparator<Executable> EXECUTABLE_COMPARATOR = (e1, e2) -> {
      int result = Boolean.compare(Modifier.isPublic(e2.getModifiers()), Modifier.isPublic(e1.getModifiers()));
      return result != 0 ? result : Integer.compare(e2.getParameterCount(), e1.getParameterCount());
   };

   public static void sortConstructors(Constructor<?>[] constructors) {
      Arrays.sort(constructors, EXECUTABLE_COMPARATOR);
   }

   public static void sortFactoryMethods(Method[] factoryMethods) {
      Arrays.sort(factoryMethods, EXECUTABLE_COMPARATOR);
   }

   public static boolean isExcludedFromDependencyCheck(PropertyDescriptor pd) {
      Method wm = pd.getWriteMethod();
      if (wm == null) {
         return false;
      }

      if (!wm.getDeclaringClass().getName().contains("$$")) {
         return false;
      }

      Class<?> superclass = wm.getDeclaringClass().getSuperclass();
      return !ClassUtils.hasMethod(superclass, wm);
   }

   public static boolean isSetterDefinedInInterface(PropertyDescriptor pd, Set<Class<?>> interfaces) {
      Method setter = pd.getWriteMethod();
      if (setter != null) {
         Class<?> targetClass = setter.getDeclaringClass();

         for (Class<?> ifc : interfaces) {
            if (ifc.isAssignableFrom(targetClass) && ClassUtils.hasMethod(ifc, setter)) {
               return true;
            }
         }
      }

      return false;
   }

   public static Object resolveAutowiringValue(Object autowiringValue, Class<?> requiredType) {
      if (autowiringValue instanceof ObjectFactory<?> factory && !requiredType.isInstance(autowiringValue)) {
         if (!(autowiringValue instanceof Serializable) || !requiredType.isInterface()) {
            return factory.getObject();
         }

         autowiringValue = Proxy.newProxyInstance(
            requiredType.getClassLoader(), new Class[]{requiredType}, new AutowireUtils.ObjectFactoryDelegatingInvocationHandler(factory)
         );
      }

      return autowiringValue;
   }

   public static Class<?> resolveReturnTypeForFactoryMethod(Method method, @Nullable Object[] args, @Nullable ClassLoader classLoader) {
      Assert.notNull(method, "Method must not be null");
      Assert.notNull(args, "Argument array must not be null");
      TypeVariable<Method>[] declaredTypeVariables = method.getTypeParameters();
      Type genericReturnType = method.getGenericReturnType();
      Type[] methodParameterTypes = method.getGenericParameterTypes();
      Assert.isTrue(args.length == methodParameterTypes.length, "Argument array does not match parameter count");
      boolean locallyDeclaredTypeVariableMatchesReturnType = false;

      for (TypeVariable<Method> currentTypeVariable : declaredTypeVariables) {
         if (currentTypeVariable.equals(genericReturnType)) {
            locallyDeclaredTypeVariableMatchesReturnType = true;
            break;
         }
      }

      if (locallyDeclaredTypeVariableMatchesReturnType) {
         for (int i = 0; i < methodParameterTypes.length; i++) {
            Type methodParameterType = methodParameterTypes[i];
            Object arg = args[i];
            if (methodParameterType.equals(genericReturnType)) {
               if (arg instanceof TypedStringValue typedValue) {
                  if (typedValue.hasTargetType()) {
                     return typedValue.getTargetType();
                  }

                  try {
                     Class<?> resolvedType = typedValue.resolveTargetType(classLoader);
                     if (resolvedType != null) {
                        return resolvedType;
                     }
                  } catch (ClassNotFoundException ex) {
                     throw new IllegalStateException("Failed to resolve value type [" + typedValue.getTargetTypeName() + "] for factory method argument", ex);
                  }
               } else if (arg != null && !(arg instanceof BeanMetadataElement)) {
                  return arg.getClass();
               }

               return method.getReturnType();
            }

            if (methodParameterType instanceof ParameterizedType parameterizedType) {
               Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();

               for (Type typeArg : actualTypeArguments) {
                  if (typeArg.equals(genericReturnType)) {
                     if (arg instanceof Class<?> clazz) {
                        return clazz;
                     }

                     String className = null;
                     if (arg instanceof String name) {
                        className = name;
                     } else if (arg instanceof TypedStringValue typedValue) {
                        String targetTypeName = typedValue.getTargetTypeName();
                        if (targetTypeName == null || Class.class.getName().equals(targetTypeName)) {
                           className = typedValue.getValue();
                        }
                     }

                     if (className != null) {
                        try {
                           return ClassUtils.forName(className, classLoader);
                        } catch (ClassNotFoundException ex) {
                           throw new IllegalStateException("Could not resolve class name [" + arg + "] for factory method argument", ex);
                        }
                     }

                     return method.getReturnType();
                  }
               }
            }
         }
      }

      return method.getReturnType();
   }

   public static boolean isAutowireCandidate(ConfigurableBeanFactory beanFactory, String beanName) {
      try {
         return beanFactory.getMergedBeanDefinition(beanName).isAutowireCandidate();
      } catch (NoSuchBeanDefinitionException ex) {
         return true;
      }
   }

   public static boolean isDefaultCandidate(ConfigurableBeanFactory beanFactory, String beanName) {
      try {
         return !(beanFactory.getMergedBeanDefinition(beanName) instanceof AbstractBeanDefinition abd && !abd.isDefaultCandidate());
      } catch (NoSuchBeanDefinitionException ex) {
         return true;
      }
   }

   private static class ObjectFactoryDelegatingInvocationHandler implements InvocationHandler, Serializable {
      private final ObjectFactory<?> objectFactory;

      ObjectFactoryDelegatingInvocationHandler(ObjectFactory<?> objectFactory) {
         this.objectFactory = objectFactory;
      }

      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
         return switch (method.getName()) {
            case "equals" -> {
               Boolean var11 = proxy == args[0];
               yield var11;
            }
            case "hashCode" -> {
               Integer var10 = System.identityHashCode(proxy);
               yield var10;
            }
            case "toString" -> {
               String var9 = this.objectFactory.toString();
               yield var9;
            }
            default -> {
               Object var6;
               try {
                  var6 = method.invoke(this.objectFactory.getObject(), args);
               } catch (InvocationTargetException ex) {
                  throw ex.getTargetException();
               }

               yield var6;
            }
         };
      }
   }
}
