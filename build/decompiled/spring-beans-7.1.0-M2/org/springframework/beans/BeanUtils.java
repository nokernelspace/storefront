package org.springframework.beans;

import java.beans.ConstructorProperties;
import java.beans.PropertyDescriptor;
import java.beans.PropertyEditor;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.reflect.KClass;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.full.KClasses;
import kotlin.reflect.jvm.KCallablesJvm;
import kotlin.reflect.jvm.ReflectJvmMapping;
import org.jspecify.annotations.Nullable;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

public abstract class BeanUtils {
   private static final Set<Class<?>> unknownEditorTypes = Collections.newSetFromMap(new ConcurrentReferenceHashMap(64));
   private static final Map<Class<?>, Object> DEFAULT_TYPE_VALUES = Map.of(
      boolean.class, false, byte.class, 0, short.class, 0, int.class, 0, long.class, 0L, float.class, 0.0F, double.class, 0.0, char.class, '\u0000'
   );
   private static final boolean KOTLIN_REFLECT_PRESENT = KotlinDetector.isKotlinReflectPresent();

   @Deprecated(since = "5.0")
   public static <T> T instantiate(Class<T> clazz) throws BeanInstantiationException {
      Assert.notNull(clazz, "Class must not be null");
      if (clazz.isInterface()) {
         throw new BeanInstantiationException(clazz, "Specified class is an interface");
      }

      try {
         return clazz.newInstance();
      } catch (InstantiationException ex) {
         throw new BeanInstantiationException(clazz, "Is it an abstract class?", ex);
      } catch (IllegalAccessException ex) {
         throw new BeanInstantiationException(clazz, "Is the constructor accessible?", ex);
      }
   }

   public static <T> T instantiateClass(Class<T> clazz) throws BeanInstantiationException {
      Assert.notNull(clazz, "Class must not be null");
      if (clazz.isInterface()) {
         throw new BeanInstantiationException(clazz, "Specified class is an interface");
      }

      Constructor<T> ctor;
      try {
         ctor = clazz.getDeclaredConstructor();
      } catch (NoSuchMethodException ex) {
         ctor = findPrimaryConstructor(clazz);
         if (ctor == null) {
            throw new BeanInstantiationException(clazz, "No default constructor found", ex);
         }
      } catch (LinkageError err) {
         throw new BeanInstantiationException(clazz, "Unresolvable class definition", err);
      }

      return instantiateClass(ctor);
   }

   public static <T> T instantiateClass(Class<?> clazz, Class<T> assignableTo) throws BeanInstantiationException {
      Assert.isAssignable(assignableTo, clazz);
      return instantiateClass((Class<T>)clazz);
   }

   public static <T> T instantiateClass(Constructor<T> ctor, Object... args) throws BeanInstantiationException {
      Assert.notNull(ctor, "Constructor must not be null");

      try {
         ReflectionUtils.makeAccessible(ctor);
         if (KOTLIN_REFLECT_PRESENT && KotlinDetector.isKotlinType(ctor.getDeclaringClass())) {
            return BeanUtils.KotlinDelegate.instantiateClass(ctor, args);
         }

         int parameterCount = ctor.getParameterCount();
         Assert.isTrue(args.length <= parameterCount, "Can't specify more arguments than constructor parameters");
         if (parameterCount == 0) {
            return ctor.newInstance();
         }

         Class<?>[] parameterTypes = ctor.getParameterTypes();
         Object[] argsWithDefaultValues = new Object[args.length];

         for (int i = 0; i < args.length; i++) {
            if (args[i] == null) {
               Class<?> parameterType = parameterTypes[i];
               argsWithDefaultValues[i] = parameterType.isPrimitive() ? DEFAULT_TYPE_VALUES.get(parameterType) : null;
            } else {
               argsWithDefaultValues[i] = args[i];
            }
         }

         return ctor.newInstance(argsWithDefaultValues);
      } catch (InstantiationException ex) {
         throw new BeanInstantiationException(ctor, "Is it an abstract class?", ex);
      } catch (IllegalAccessException ex) {
         throw new BeanInstantiationException(ctor, "Is the constructor accessible?", ex);
      } catch (IllegalArgumentException ex) {
         throw new BeanInstantiationException(ctor, "Illegal arguments for constructor", ex);
      } catch (InvocationTargetException ex) {
         throw new BeanInstantiationException(ctor, "Constructor threw exception", ex.getTargetException());
      }
   }

   public static <T> Constructor<T> getResolvableConstructor(Class<T> clazz) {
      Constructor<T> ctor = findPrimaryConstructor(clazz);
      if (ctor != null) {
         return ctor;
      }

      Constructor<?>[] ctors = clazz.getConstructors();
      if (ctors.length == 1) {
         return (Constructor<T>)ctors[0];
      }

      if (ctors.length == 0) {
         ctors = clazz.getDeclaredConstructors();
         if (ctors.length == 1) {
            return (Constructor<T>)ctors[0];
         }
      }

      try {
         return clazz.getDeclaredConstructor();
      } catch (NoSuchMethodException var4) {
         throw new IllegalStateException("No primary or single unique constructor found for " + clazz);
      }
   }

   public static <T> @Nullable Constructor<T> findPrimaryConstructor(Class<T> clazz) {
      Assert.notNull(clazz, "Class must not be null");
      if (KOTLIN_REFLECT_PRESENT && KotlinDetector.isKotlinType(clazz)) {
         return BeanUtils.KotlinDelegate.findPrimaryConstructor(clazz);
      }

      if (clazz.isRecord()) {
         try {
            RecordComponent[] components = clazz.getRecordComponents();
            Class<?>[] paramTypes = new Class[components.length];

            for (int i = 0; i < components.length; i++) {
               paramTypes[i] = components[i].getType();
            }

            return clazz.getDeclaredConstructor(paramTypes);
         } catch (NoSuchMethodException var4) {
         }
      }

      return null;
   }

   public static @Nullable Method findMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
      try {
         return clazz.getMethod(methodName, paramTypes);
      } catch (NoSuchMethodException ex) {
         return findDeclaredMethod(clazz, methodName, paramTypes);
      }
   }

   public static @Nullable Method findDeclaredMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
      try {
         return clazz.getDeclaredMethod(methodName, paramTypes);
      } catch (NoSuchMethodException ex) {
         return clazz.getSuperclass() != null ? findDeclaredMethod(clazz.getSuperclass(), methodName, paramTypes) : null;
      }
   }

   public static @Nullable Method findMethodWithMinimalParameters(Class<?> clazz, String methodName) throws IllegalArgumentException {
      Method targetMethod = findMethodWithMinimalParameters(clazz.getMethods(), methodName);
      if (targetMethod == null) {
         targetMethod = findDeclaredMethodWithMinimalParameters(clazz, methodName);
      }

      return targetMethod;
   }

   public static @Nullable Method findDeclaredMethodWithMinimalParameters(Class<?> clazz, String methodName) throws IllegalArgumentException {
      Method targetMethod = findMethodWithMinimalParameters(clazz.getDeclaredMethods(), methodName);
      if (targetMethod == null && clazz.getSuperclass() != null) {
         targetMethod = findDeclaredMethodWithMinimalParameters(clazz.getSuperclass(), methodName);
      }

      return targetMethod;
   }

   public static @Nullable Method findMethodWithMinimalParameters(Method[] methods, String methodName) throws IllegalArgumentException {
      Method targetMethod = null;
      int numMethodsFoundWithCurrentMinimumArgs = 0;

      for (Method method : methods) {
         if (method.getName().equals(methodName)) {
            int numParams = method.getParameterCount();
            if (targetMethod == null || numParams < targetMethod.getParameterCount()) {
               targetMethod = method;
               numMethodsFoundWithCurrentMinimumArgs = 1;
            } else if (!method.isBridge() && targetMethod.getParameterCount() == numParams) {
               if (targetMethod.isBridge()) {
                  targetMethod = method;
               } else {
                  numMethodsFoundWithCurrentMinimumArgs++;
               }
            }
         }
      }

      if (numMethodsFoundWithCurrentMinimumArgs > 1) {
         throw new IllegalArgumentException(
            "Cannot resolve method '"
               + methodName
               + "' to a unique method. Attempted to resolve to overloaded method with the least number of parameters but there were "
               + numMethodsFoundWithCurrentMinimumArgs
               + " candidates."
         );
      } else {
         return targetMethod;
      }
   }

   public static @Nullable Method resolveSignature(String signature, Class<?> clazz) {
      Assert.hasText(signature, "'signature' must not be empty");
      Assert.notNull(clazz, "Class must not be null");
      int startParen = signature.indexOf(40);
      int endParen = signature.indexOf(41);
      if (startParen > -1 && endParen == -1) {
         throw new IllegalArgumentException("Invalid method signature '" + signature + "': expected closing ')' for args list");
      }

      if (startParen == -1 && endParen > -1) {
         throw new IllegalArgumentException("Invalid method signature '" + signature + "': expected opening '(' for args list");
      }

      if (startParen == -1) {
         return findMethodWithMinimalParameters(clazz, signature);
      }

      String methodName = signature.substring(0, startParen);
      String[] parameterTypeNames = StringUtils.commaDelimitedListToStringArray(signature.substring(startParen + 1, endParen));
      Class<?>[] parameterTypes = new Class[parameterTypeNames.length];

      for (int i = 0; i < parameterTypeNames.length; i++) {
         String parameterTypeName = parameterTypeNames[i].trim();

         try {
            parameterTypes[i] = ClassUtils.forName(parameterTypeName, clazz.getClassLoader());
         } catch (Throwable ex) {
            throw new IllegalArgumentException(
               "Invalid method signature: unable to resolve type [" + parameterTypeName + "] for argument " + i + ". Root cause: " + ex
            );
         }
      }

      return findMethod(clazz, methodName, parameterTypes);
   }

   public static PropertyDescriptor[] getPropertyDescriptors(Class<?> clazz) throws BeansException {
      return CachedIntrospectionResults.forClass(clazz).getPropertyDescriptors();
   }

   public static @Nullable PropertyDescriptor getPropertyDescriptor(Class<?> clazz, String propertyName) throws BeansException {
      return CachedIntrospectionResults.forClass(clazz).getPropertyDescriptor(propertyName);
   }

   public static @Nullable PropertyDescriptor findPropertyForMethod(Method method) throws BeansException {
      return findPropertyForMethod(method, method.getDeclaringClass());
   }

   public static @Nullable PropertyDescriptor findPropertyForMethod(Method method, Class<?> clazz) throws BeansException {
      Assert.notNull(method, "Method must not be null");
      PropertyDescriptor[] pds = getPropertyDescriptors(clazz);

      for (PropertyDescriptor pd : pds) {
         if (method.equals(pd.getReadMethod()) || method.equals(pd.getWriteMethod())) {
            return pd;
         }
      }

      return null;
   }

   public static @Nullable PropertyEditor findEditorByConvention(@Nullable Class<?> targetType) {
      if (targetType != null && !targetType.isArray() && !unknownEditorTypes.contains(targetType)) {
         ClassLoader cl = targetType.getClassLoader();
         if (cl == null) {
            try {
               cl = ClassLoader.getSystemClassLoader();
               if (cl == null) {
                  return null;
               }
            } catch (Throwable ex) {
               return null;
            }
         }

         String targetTypeName = targetType.getName();
         String editorName = targetTypeName + "Editor";

         try {
            Class<?> editorClass = cl.loadClass(editorName);
            if (editorClass != null) {
               if (!PropertyEditor.class.isAssignableFrom(editorClass)) {
                  unknownEditorTypes.add(targetType);
                  return null;
               }

               return instantiateClass((Class<PropertyEditor>)editorClass);
            }
         } catch (ClassNotFoundException var5) {
         }

         unknownEditorTypes.add(targetType);
         return null;
      } else {
         return null;
      }
   }

   public static Class<?> findPropertyType(String propertyName, Class<?> @Nullable ... beanClasses) {
      if (beanClasses != null) {
         for (Class<?> beanClass : beanClasses) {
            PropertyDescriptor pd = getPropertyDescriptor(beanClass, propertyName);
            if (pd != null) {
               return pd.getPropertyType();
            }
         }
      }

      return Object.class;
   }

   public static boolean hasUniqueWriteMethod(PropertyDescriptor pd) {
      return pd instanceof GenericTypeAwarePropertyDescriptor gpd ? gpd.hasUniqueWriteMethod() : pd.getWriteMethod() != null;
   }

   public static MethodParameter getWriteMethodParameter(PropertyDescriptor pd) {
      if (pd instanceof GenericTypeAwarePropertyDescriptor gpd) {
         return new MethodParameter(gpd.getWriteMethodParameter());
      } else {
         Method writeMethod = pd.getWriteMethod();
         Assert.state(writeMethod != null, "No write method available");
         return new MethodParameter(writeMethod, 0);
      }
   }

   public static String[] getParameterNames(Constructor<?> ctor) {
      ConstructorProperties cp = ctor.getAnnotation(ConstructorProperties.class);
      String[] paramNames = cp != null ? cp.value() : DefaultParameterNameDiscoverer.getSharedInstance().getParameterNames(ctor);
      Assert.state(paramNames != null, () -> "Cannot resolve parameter names for constructor " + ctor);
      int parameterCount = KOTLIN_REFLECT_PRESENT && BeanUtils.KotlinDelegate.hasDefaultConstructorMarker(ctor)
         ? ctor.getParameterCount() - 1
         : ctor.getParameterCount();
      Assert.state(paramNames.length == parameterCount, () -> "Invalid number of parameter names: " + paramNames.length + " for constructor " + ctor);
      return paramNames;
   }

   public static boolean isSimpleProperty(Class<?> type) {
      Assert.notNull(type, "'type' must not be null");
      return isSimpleValueType(type) || type.isArray() && isSimpleValueType(type.componentType());
   }

   public static boolean isSimpleValueType(Class<?> type) {
      return ClassUtils.isSimpleValueType(type);
   }

   public static void copyProperties(Object source, Object target) throws BeansException {
      copyProperties(source, target, null, (String[])null);
   }

   public static void copyProperties(Object source, Object target, Class<?> editable) throws BeansException {
      copyProperties(source, target, editable, (String[])null);
   }

   public static void copyProperties(Object source, Object target, String... ignoreProperties) throws BeansException {
      copyProperties(source, target, null, ignoreProperties);
   }

   private static void copyProperties(Object source, Object target, @Nullable Class<?> editable, String @Nullable ... ignoreProperties) throws BeansException {
      Assert.notNull(source, "Source must not be null");
      Assert.notNull(target, "Target must not be null");
      Class<?> actualEditable = target.getClass();
      if (editable != null) {
         if (!editable.isInstance(target)) {
            throw new IllegalArgumentException(
               "Target class [" + target.getClass().getName() + "] not assignable to editable class [" + editable.getName() + "]"
            );
         }

         actualEditable = editable;
      }

      PropertyDescriptor[] targetPds = getPropertyDescriptors(actualEditable);
      Set<String> ignoredProps = ignoreProperties != null ? new HashSet<>(Arrays.asList(ignoreProperties)) : null;
      CachedIntrospectionResults sourceResults = actualEditable != source.getClass() ? CachedIntrospectionResults.forClass(source.getClass()) : null;

      for (PropertyDescriptor targetPd : targetPds) {
         Method writeMethod = targetPd.getWriteMethod();
         if (writeMethod != null && (ignoredProps == null || !ignoredProps.contains(targetPd.getName()))) {
            PropertyDescriptor sourcePd = sourceResults != null ? sourceResults.getPropertyDescriptor(targetPd.getName()) : targetPd;
            if (sourcePd != null) {
               Method readMethod = sourcePd.getReadMethod();
               if (readMethod != null && isAssignable(writeMethod, readMethod, sourcePd, targetPd)) {
                  try {
                     ReflectionUtils.makeAccessible(readMethod);
                     Object value = readMethod.invoke(source);
                     ReflectionUtils.makeAccessible(writeMethod);
                     writeMethod.invoke(target, value);
                  } catch (Throwable ex) {
                     throw new FatalBeanException("Could not copy property '" + targetPd.getName() + "' from source to target", ex);
                  }
               }
            }
         }
      }
   }

   private static boolean isAssignable(Method writeMethod, Method readMethod, PropertyDescriptor sourcePd, PropertyDescriptor targetPd) {
      Type paramType = writeMethod.getGenericParameterTypes()[0];
      if (paramType instanceof Class<?> clazz) {
         return ClassUtils.isAssignable(clazz, readMethod.getReturnType());
      } else {
         if (paramType.equals(readMethod.getGenericReturnType())) {
            return true;
         }

         ResolvableType sourceType = ((GenericTypeAwarePropertyDescriptor)sourcePd).getReadMethodType();
         ResolvableType targetType = ((GenericTypeAwarePropertyDescriptor)targetPd).getWriteMethodType();
         return !sourceType.hasUnresolvableGenerics() && !targetType.hasUnresolvableGenerics()
            ? targetType.isAssignableFrom(sourceType)
            : ClassUtils.isAssignable(writeMethod.getParameterTypes()[0], readMethod.getReturnType());
      }
   }

   private static class KotlinDelegate {
      public static <T> @Nullable Constructor<T> findPrimaryConstructor(Class<T> clazz) {
         try {
            KClass<T> kClass = JvmClassMappingKt.getKotlinClass(clazz);
            KFunction<T> primaryCtor = KClasses.getPrimaryConstructor(kClass);
            if (primaryCtor == null) {
               return null;
            } else if (KotlinDetector.isInlineClass(clazz)) {
               Constructor<?>[] constructors = clazz.getDeclaredConstructors();
               Assert.state(constructors.length == 1, "Kotlin value classes annotated with @JvmInline are expected to have a single JVM constructor");
               return (Constructor<T>)constructors[0];
            } else {
               Constructor<T> constructor = ReflectJvmMapping.getJavaConstructor(primaryCtor);
               if (constructor == null) {
                  throw new IllegalStateException("Failed to find Java constructor for Kotlin primary constructor: " + clazz.getName());
               } else {
                  return constructor;
               }
            }
         } catch (UnsupportedOperationException ex) {
            return null;
         }
      }

      public static <T> T instantiateClass(Constructor<T> ctor, @Nullable Object... args) throws IllegalAccessException, InvocationTargetException, InstantiationException {
         KFunction<T> kotlinConstructor = ReflectJvmMapping.getKotlinFunction(ctor);
         if (kotlinConstructor == null) {
            return ctor.newInstance(args);
         }

         if (!Modifier.isPublic(ctor.getModifiers()) || !Modifier.isPublic(ctor.getDeclaringClass().getModifiers())) {
            KCallablesJvm.setAccessible(kotlinConstructor, true);
         }

         List<KParameter> parameters = kotlinConstructor.getParameters();
         Assert.isTrue(args.length <= parameters.size(), "Number of provided arguments must be less than or equal to the number of constructor parameters");
         if (parameters.isEmpty()) {
            return (T)kotlinConstructor.call(new Object[0]);
         }

         Map<KParameter, Object> argParameters = CollectionUtils.newHashMap(parameters.size());

         for (int i = 0; i < args.length; i++) {
            if (!parameters.get(i).isOptional() || args[i] != null) {
               argParameters.put(parameters.get(i), args[i]);
            }
         }

         return (T)kotlinConstructor.callBy(argParameters);
      }

      public static boolean hasDefaultConstructorMarker(Constructor<?> ctor) {
         int parameterCount = ctor.getParameterCount();
         return parameterCount > 0 && ctor.getParameters()[parameterCount - 1].getType() == DefaultConstructorMarker.class;
      }
   }
}
