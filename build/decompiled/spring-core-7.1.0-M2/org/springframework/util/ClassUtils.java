package org.springframework.util;

import java.io.Closeable;
import java.io.Externalizable;
import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.temporal.Temporal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Currency;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TimeZone;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.lang.Contract;

public abstract class ClassUtils {
   public static final String ARRAY_SUFFIX = "[]";
   private static final Class<?>[] EMPTY_CLASS_ARRAY = new Class[0];
   private static final char PACKAGE_SEPARATOR = '.';
   private static final char PATH_SEPARATOR = '/';
   private static final char NESTED_CLASS_SEPARATOR = '$';
   public static final String CGLIB_CLASS_SEPARATOR = "$$";
   public static final String CLASS_FILE_SUFFIX = ".class";
   public static final String PACKAGE_INFO_SUFFIX = ".package-info";
   private static final int NON_OVERRIDABLE_MODIFIER = 26;
   private static final int OVERRIDABLE_MODIFIER = 5;
   private static final Map<Class<?>, Class<?>> primitiveWrapperTypeMap = new IdentityHashMap<>(9);
   private static final Map<Class<?>, Class<?>> primitiveTypeToWrapperMap = new IdentityHashMap<>(9);
   private static final Map<String, Class<?>> primitiveTypeNameMap = new HashMap<>(32);
   private static final Map<String, Class<?>> commonClassCache = new HashMap<>(64);
   private static final Set<Class<?>> javaLanguageInterfaces;
   private static final Map<Method, Method> interfaceMethodCache = new ConcurrentReferenceHashMap<>(256);
   private static final Map<Method, Method> publicInterfaceMethodCache = new ConcurrentReferenceHashMap<>(256);
   private static final Map<Method, Method> publiclyAccessibleMethodCache = new ConcurrentReferenceHashMap<>(256);

   private static void registerCommonClasses(Class<?>... commonClasses) {
      for (Class<?> clazz : commonClasses) {
         commonClassCache.put(clazz.getName(), clazz);
      }
   }

   public static @Nullable ClassLoader getDefaultClassLoader() {
      ClassLoader cl = null;

      try {
         cl = Thread.currentThread().getContextClassLoader();
      } catch (Throwable var3) {
      }

      if (cl == null) {
         cl = ClassUtils.class.getClassLoader();
         if (cl == null) {
            try {
               cl = ClassLoader.getSystemClassLoader();
            } catch (Throwable var2) {
            }
         }
      }

      return cl;
   }

   @Contract("null -> null")
   public static @Nullable ClassLoader overrideThreadContextClassLoader(@Nullable ClassLoader classLoaderToUse) {
      Thread currentThread = Thread.currentThread();
      ClassLoader threadContextClassLoader = currentThread.getContextClassLoader();
      if (classLoaderToUse != null && !classLoaderToUse.equals(threadContextClassLoader)) {
         currentThread.setContextClassLoader(classLoaderToUse);
         return threadContextClassLoader;
      } else {
         return null;
      }
   }

   public static Class<?> forName(String name, @Nullable ClassLoader classLoader) throws ClassNotFoundException, LinkageError {
      Assert.notNull(name, "Name must not be null");
      Class<?> clazz = resolvePrimitiveClassName(name);
      if (clazz == null) {
         clazz = commonClassCache.get(name);
      }

      if (clazz != null) {
         return clazz;
      }

      if (name.endsWith("[]")) {
         String elementClassName = name.substring(0, name.length() - "[]".length());
         Class<?> elementClass = forName(elementClassName, classLoader);
         return elementClass.arrayType();
      }

      ClassLoader clToUse = classLoader;
      if (clToUse == null) {
         clToUse = getDefaultClassLoader();
      }

      try {
         return Class.forName(name, false, clToUse);
      } catch (ClassNotFoundException ex) {
         int lastDotIndex = name.lastIndexOf(46);
         int previousDotIndex = name.lastIndexOf(46, lastDotIndex - 1);
         if (lastDotIndex != -1 && previousDotIndex != -1 && Character.isUpperCase(name.charAt(previousDotIndex + 1))) {
            String nestedClassName = name.substring(0, lastDotIndex) + "$" + name.substring(lastDotIndex + 1);

            try {
               return Class.forName(nestedClassName, false, clToUse);
            } catch (ClassNotFoundException var9) {
            }
         }

         throw ex;
      }
   }

   public static Class<?> resolveClassName(String className, @Nullable ClassLoader classLoader) throws IllegalArgumentException {
      try {
         return forName(className, classLoader);
      } catch (IllegalAccessError err) {
         throw new IllegalStateException("Readability mismatch in inheritance hierarchy of class [" + className + "]: " + err.getMessage(), err);
      } catch (LinkageError err) {
         throw new IllegalArgumentException("Unresolvable class definition for class [" + className + "]", err);
      } catch (ClassNotFoundException ex) {
         throw new IllegalArgumentException("Could not find class [" + className + "]", ex);
      }
   }

   public static boolean isPresent(String className, @Nullable ClassLoader classLoader) {
      try {
         forName(className, classLoader);
         return true;
      } catch (IllegalAccessError err) {
         throw new IllegalStateException("Readability mismatch in inheritance hierarchy of class [" + className + "]: " + err.getMessage(), err);
      } catch (Throwable ex) {
         return false;
      }
   }

   @Contract("_, null -> true")
   public static boolean isVisible(Class<?> clazz, @Nullable ClassLoader classLoader) {
      if (classLoader == null) {
         return true;
      }

      try {
         if (clazz.getClassLoader() == classLoader) {
            return true;
         }
      } catch (SecurityException var3) {
      }

      return isLoadable(clazz, classLoader);
   }

   public static boolean isCacheSafe(Class<?> clazz, @Nullable ClassLoader classLoader) {
      Assert.notNull(clazz, "Class must not be null");

      try {
         ClassLoader target = clazz.getClassLoader();
         if (target == classLoader || target == null) {
            return true;
         }

         if (classLoader == null) {
            return false;
         }

         ClassLoader current = classLoader;

         while (current != null) {
            current = current.getParent();
            if (current == target) {
               return true;
            }
         }

         while (target != null) {
            target = target.getParent();
            if (target == classLoader) {
               return false;
            }
         }
      } catch (SecurityException var4) {
      }

      return classLoader != null && isLoadable(clazz, classLoader);
   }

   private static boolean isLoadable(Class<?> clazz, ClassLoader classLoader) {
      try {
         return clazz == classLoader.loadClass(clazz.getName());
      } catch (ClassNotFoundException ex) {
         return false;
      }
   }

   @Contract("null -> null")
   public static @Nullable Class<?> resolvePrimitiveClassName(@Nullable String name) {
      Class<?> result = null;
      if (name != null && name.length() <= 7) {
         result = primitiveTypeNameMap.get(name);
      }

      return result;
   }

   public static boolean isPrimitiveWrapper(Class<?> clazz) {
      Assert.notNull(clazz, "Class must not be null");
      return primitiveWrapperTypeMap.containsKey(clazz);
   }

   public static boolean isPrimitiveOrWrapper(Class<?> clazz) {
      Assert.notNull(clazz, "Class must not be null");
      return clazz.isPrimitive() || isPrimitiveWrapper(clazz);
   }

   public static boolean isPrimitiveArray(Class<?> clazz) {
      Assert.notNull(clazz, "Class must not be null");
      return clazz.isArray() && clazz.componentType().isPrimitive();
   }

   public static boolean isPrimitiveWrapperArray(Class<?> clazz) {
      Assert.notNull(clazz, "Class must not be null");
      return clazz.isArray() && isPrimitiveWrapper(clazz.componentType());
   }

   public static Class<?> resolvePrimitiveIfNecessary(Class<?> clazz) {
      Assert.notNull(clazz, "Class must not be null");
      return clazz.isPrimitive() && clazz != void.class ? primitiveTypeToWrapperMap.get(clazz) : clazz;
   }

   @Contract("null -> false")
   public static boolean isVoidType(@Nullable Class<?> type) {
      return type == void.class || type == Void.class;
   }

   public static boolean isSimpleValueType(Class<?> type) {
      return !isVoidType(type)
         && (
            isPrimitiveOrWrapper(type)
               || Enum.class.isAssignableFrom(type)
               || CharSequence.class.isAssignableFrom(type)
               || Number.class.isAssignableFrom(type)
               || Date.class.isAssignableFrom(type)
               || Temporal.class.isAssignableFrom(type)
               || ZoneId.class.isAssignableFrom(type)
               || TimeZone.class.isAssignableFrom(type)
               || File.class.isAssignableFrom(type)
               || Path.class.isAssignableFrom(type)
               || Charset.class.isAssignableFrom(type)
               || Currency.class.isAssignableFrom(type)
               || InetAddress.class.isAssignableFrom(type)
               || URI.class == type
               || URL.class == type
               || UUID.class == type
               || Locale.class == type
               || Pattern.class == type
               || Class.class == type
         );
   }

   public static boolean isAssignable(Class<?> lhsType, Class<?> rhsType) {
      Assert.notNull(lhsType, "Left-hand side type must not be null");
      Assert.notNull(rhsType, "Right-hand side type must not be null");
      if (lhsType.isAssignableFrom(rhsType)) {
         return true;
      }

      if (lhsType.isPrimitive()) {
         Class<?> resolvedPrimitive = primitiveWrapperTypeMap.get(rhsType);
         return lhsType == resolvedPrimitive;
      }

      if (!rhsType.isPrimitive()) {
         return false;
      }

      Class<?> resolvedWrapper = primitiveTypeToWrapperMap.get(rhsType);
      return resolvedWrapper != null && lhsType.isAssignableFrom(resolvedWrapper);
   }

   public static boolean isAssignableValue(Class<?> type, @Nullable Object value) {
      Assert.notNull(type, "Type must not be null");
      return value != null ? isAssignable(type, value.getClass()) : !type.isPrimitive();
   }

   public static String convertResourcePathToClassName(String resourcePath) {
      Assert.notNull(resourcePath, "Resource path must not be null");
      return resourcePath.replace('/', '.');
   }

   public static String convertClassNameToResourcePath(String className) {
      Assert.notNull(className, "Class name must not be null");
      return className.replace('.', '/');
   }

   public static String addResourcePathToPackagePath(Class<?> clazz, String resourceName) {
      Assert.notNull(resourceName, "Resource name must not be null");
      return !resourceName.startsWith("/") ? classPackageAsResourcePath(clazz) + "/" + resourceName : classPackageAsResourcePath(clazz) + resourceName;
   }

   public static String classPackageAsResourcePath(@Nullable Class<?> clazz) {
      if (clazz == null) {
         return "";
      }

      String className = clazz.getName();
      int packageEndIndex = className.lastIndexOf(46);
      if (packageEndIndex == -1) {
         return "";
      }

      String packageName = className.substring(0, packageEndIndex);
      return packageName.replace('.', '/');
   }

   public static String classNamesToString(Class<?>... classes) {
      return classNamesToString(Arrays.asList(classes));
   }

   public static String classNamesToString(@Nullable Collection<Class<?>> classes) {
      if (CollectionUtils.isEmpty(classes)) {
         return "[]";
      }

      StringJoiner stringJoiner = new StringJoiner(", ", "[", "]");

      for (Class<?> clazz : classes) {
         stringJoiner.add(clazz.getName());
      }

      return stringJoiner.toString();
   }

   public static Class<?>[] toClassArray(@Nullable Collection<Class<?>> collection) {
      return !CollectionUtils.isEmpty(collection) ? collection.toArray(EMPTY_CLASS_ARRAY) : EMPTY_CLASS_ARRAY;
   }

   public static Class<?>[] getAllInterfaces(Object instance) {
      Assert.notNull(instance, "Instance must not be null");
      return getAllInterfacesForClass(instance.getClass());
   }

   public static Class<?>[] getAllInterfacesForClass(Class<?> clazz) {
      return getAllInterfacesForClass(clazz, null);
   }

   public static Class<?>[] getAllInterfacesForClass(Class<?> clazz, @Nullable ClassLoader classLoader) {
      return toClassArray(getAllInterfacesForClassAsSet(clazz, classLoader));
   }

   public static Set<Class<?>> getAllInterfacesAsSet(Object instance) {
      Assert.notNull(instance, "Instance must not be null");
      return getAllInterfacesForClassAsSet(instance.getClass());
   }

   public static Set<Class<?>> getAllInterfacesForClassAsSet(Class<?> clazz) {
      return getAllInterfacesForClassAsSet(clazz, null);
   }

   public static Set<Class<?>> getAllInterfacesForClassAsSet(Class<?> clazz, @Nullable ClassLoader classLoader) {
      Assert.notNull(clazz, "Class must not be null");
      if (clazz.isInterface() && isVisible(clazz, classLoader)) {
         return Collections.singleton(clazz);
      }

      Set<Class<?>> interfaces = new LinkedHashSet<>();

      for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
         Class<?>[] ifcs = current.getInterfaces();

         for (Class<?> ifc : ifcs) {
            if (isVisible(ifc, classLoader)) {
               interfaces.add(ifc);
            }
         }
      }

      return interfaces;
   }

   public static Class<?> createCompositeInterface(Class<?>[] interfaces, @Nullable ClassLoader classLoader) {
      Assert.notEmpty(interfaces, "Interface array must not be empty");
      return Proxy.getProxyClass(classLoader, interfaces);
   }

   @Contract("null, _ -> param2; _, null -> param1")
   public static @Nullable Class<?> determineCommonAncestor(@Nullable Class<?> clazz1, @Nullable Class<?> clazz2) {
      if (clazz1 == null) {
         return clazz2;
      }

      if (clazz2 == null) {
         return clazz1;
      }

      if (clazz1.isAssignableFrom(clazz2)) {
         return clazz1;
      }

      if (clazz2.isAssignableFrom(clazz1)) {
         return clazz2;
      }

      Class<?> ancestor = clazz1;

      do {
         ancestor = ancestor.getSuperclass();
         if (ancestor == null || Object.class == ancestor) {
            return null;
         }
      } while (!ancestor.isAssignableFrom(clazz2));

      return ancestor;
   }

   public static boolean isJavaLanguageInterface(Class<?> ifc) {
      return javaLanguageInterfaces.contains(ifc);
   }

   public static boolean isStaticClass(Class<?> clazz) {
      return Modifier.isStatic(clazz.getModifiers());
   }

   public static boolean isInnerClass(Class<?> clazz) {
      return clazz.isMemberClass() && !isStaticClass(clazz);
   }

   public static boolean isLambdaClass(Class<?> clazz) {
      return clazz.isSynthetic() && clazz.getSuperclass() == Object.class && clazz.getInterfaces().length > 0 && clazz.getName().contains("$$Lambda");
   }

   @Deprecated(since = "5.2")
   public static boolean isCglibProxy(Object object) {
      return isCglibProxyClass(object.getClass());
   }

   @Deprecated(since = "5.2")
   @Contract("null -> false")
   public static boolean isCglibProxyClass(@Nullable Class<?> clazz) {
      return clazz != null && isCglibProxyClassName(clazz.getName());
   }

   @Deprecated(since = "5.2")
   @Contract("null -> false")
   public static boolean isCglibProxyClassName(@Nullable String className) {
      return className != null && className.contains("$$");
   }

   public static Class<?> getUserClass(Object instance) {
      Assert.notNull(instance, "Instance must not be null");
      return getUserClass(instance.getClass());
   }

   public static Class<?> getUserClass(Class<?> clazz) {
      if (clazz.getName().contains("$$")) {
         Class<?> superclass = clazz.getSuperclass();
         if (superclass != null && superclass != Object.class) {
            return superclass;
         }
      }

      return clazz;
   }

   @Contract("null -> null")
   public static @Nullable String getDescriptiveType(@Nullable Object value) {
      if (value == null) {
         return null;
      }

      Class<?> clazz = value.getClass();
      if (!Proxy.isProxyClass(clazz)) {
         return clazz.getTypeName();
      }

      String prefix = clazz.getTypeName() + " implementing ";
      StringJoiner result = new StringJoiner(",", prefix, "");

      for (Class<?> ifc : clazz.getInterfaces()) {
         result.add(ifc.getTypeName());
      }

      return result.toString();
   }

   @Contract("_, null -> false")
   public static boolean matchesTypeName(Class<?> clazz, @Nullable String typeName) {
      return typeName != null && (typeName.equals(clazz.getTypeName()) || typeName.equals(clazz.getSimpleName()));
   }

   public static String getShortName(String className) {
      Assert.hasLength(className, "Class name must not be empty");
      int lastDotIndex = className.lastIndexOf(46);
      int nameEndIndex = className.indexOf("$$");
      if (nameEndIndex == -1) {
         nameEndIndex = className.length();
      }

      String shortName = className.substring(lastDotIndex + 1, nameEndIndex);
      return shortName.replace('$', '.');
   }

   public static String getShortName(Class<?> clazz) {
      return getShortName(getQualifiedName(clazz));
   }

   public static String getShortNameAsProperty(Class<?> clazz) {
      String shortName = getShortName(clazz);
      int dotIndex = shortName.lastIndexOf(46);
      shortName = dotIndex != -1 ? shortName.substring(dotIndex + 1) : shortName;
      return StringUtils.uncapitalizeAsProperty(shortName);
   }

   public static String getClassFileName(Class<?> clazz) {
      Assert.notNull(clazz, "Class must not be null");
      String className = clazz.getName();
      int lastDotIndex = className.lastIndexOf(46);
      return className.substring(lastDotIndex + 1) + ".class";
   }

   public static String getPackageName(Class<?> clazz) {
      Assert.notNull(clazz, "Class must not be null");
      return getPackageName(clazz.getName());
   }

   public static String getPackageName(String fqClassName) {
      Assert.notNull(fqClassName, "Class name must not be null");
      int lastDotIndex = fqClassName.lastIndexOf(46);
      return lastDotIndex != -1 ? fqClassName.substring(0, lastDotIndex) : "";
   }

   public static String getCanonicalName(Class<?> clazz) {
      Assert.notNull(clazz, "Class must not be null");
      String canonicalName = clazz.getCanonicalName();
      return canonicalName != null ? canonicalName : clazz.getTypeName();
   }

   public static String getQualifiedName(Class<?> clazz) {
      Assert.notNull(clazz, "Class must not be null");
      return clazz.getTypeName();
   }

   public static String getQualifiedMethodName(Method method) {
      return getQualifiedMethodName(method, null);
   }

   public static String getQualifiedMethodName(Method method, @Nullable Class<?> clazz) {
      Assert.notNull(method, "Method must not be null");
      return (clazz != null ? clazz : method.getDeclaringClass()).getName() + "." + method.getName();
   }

   public static boolean hasConstructor(Class<?> clazz, Class<?>... paramTypes) {
      return getConstructorIfAvailable(clazz, paramTypes) != null;
   }

   public static <T> @Nullable Constructor<T> getConstructorIfAvailable(Class<T> clazz, Class<?>... paramTypes) {
      Assert.notNull(clazz, "Class must not be null");

      try {
         return clazz.getConstructor(paramTypes);
      } catch (NoSuchMethodException ex) {
         return null;
      }
   }

   public static boolean hasMethod(Class<?> clazz, Method method) {
      Assert.notNull(clazz, "Class must not be null");
      Assert.notNull(method, "Method must not be null");
      if (clazz == method.getDeclaringClass()) {
         return true;
      }

      String methodName = method.getName();
      Class<?>[] paramTypes = method.getParameterTypes();
      return getMethodOrNull(clazz, methodName, paramTypes) != null;
   }

   public static boolean hasMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
      return getMethodIfAvailable(clazz, methodName, paramTypes) != null;
   }

   public static Method getMethod(Class<?> clazz, String methodName, @Nullable Class<?>... paramTypes) {
      Assert.notNull(clazz, "Class must not be null");
      Assert.notNull(methodName, "Method name must not be null");
      if (paramTypes != null) {
         try {
            return clazz.getMethod(methodName, paramTypes);
         } catch (NoSuchMethodException ex) {
            throw new IllegalStateException("Expected method not found: " + ex);
         }
      } else {
         Set<Method> candidates = findMethodCandidatesByName(clazz, methodName);
         if (candidates.size() == 1) {
            return candidates.iterator().next();
         } else if (candidates.isEmpty()) {
            throw new IllegalStateException("Expected method not found: " + clazz.getName() + "." + methodName);
         } else {
            throw new IllegalStateException("No unique method found: " + clazz.getName() + "." + methodName);
         }
      }
   }

   public static @Nullable Method getMethodIfAvailable(Class<?> clazz, String methodName, @Nullable Class<?> @Nullable ... paramTypes) {
      Assert.notNull(clazz, "Class must not be null");
      Assert.notNull(methodName, "Method name must not be null");
      if (paramTypes != null) {
         return getMethodOrNull(clazz, methodName, paramTypes);
      }

      Set<Method> candidates = findMethodCandidatesByName(clazz, methodName);
      return candidates.size() == 1 ? candidates.iterator().next() : null;
   }

   public static int getMethodCountForName(Class<?> clazz, String methodName) {
      Assert.notNull(clazz, "Class must not be null");
      Assert.notNull(methodName, "Method name must not be null");
      int count = 0;
      Method[] declaredMethods = clazz.getDeclaredMethods();

      for (Method method : declaredMethods) {
         if (methodName.equals(method.getName())) {
            count++;
         }
      }

      Class<?>[] ifcs = clazz.getInterfaces();

      for (Class<?> ifc : ifcs) {
         count += getMethodCountForName(ifc, methodName);
      }

      if (clazz.getSuperclass() != null) {
         count += getMethodCountForName(clazz.getSuperclass(), methodName);
      }

      return count;
   }

   public static boolean hasAtLeastOneMethodWithName(Class<?> clazz, String methodName) {
      Assert.notNull(clazz, "Class must not be null");
      Assert.notNull(methodName, "Method name must not be null");
      Method[] declaredMethods = clazz.getDeclaredMethods();

      for (Method method : declaredMethods) {
         if (method.getName().equals(methodName)) {
            return true;
         }
      }

      Class<?>[] ifcs = clazz.getInterfaces();

      for (Class<?> ifc : ifcs) {
         if (hasAtLeastOneMethodWithName(ifc, methodName)) {
            return true;
         }
      }

      return clazz.getSuperclass() != null && hasAtLeastOneMethodWithName(clazz.getSuperclass(), methodName);
   }

   public static Method getMostSpecificMethod(Method method, @Nullable Class<?> targetClass) {
      if (targetClass != null
         && targetClass != method.getDeclaringClass()
         && (isOverridable(method, targetClass) || !method.getDeclaringClass().isAssignableFrom(targetClass))) {
         try {
            if (Modifier.isPublic(method.getModifiers())) {
               try {
                  return targetClass.getMethod(method.getName(), method.getParameterTypes());
               } catch (NoSuchMethodException ex) {
                  return method;
               }
            }

            Method specificMethod = ReflectionUtils.findMethod(targetClass, method.getName(), method.getParameterTypes());
            return specificMethod != null ? specificMethod : method;
         } catch (SecurityException var4) {
         }
      }

      return method;
   }

   @Deprecated(since = "5.2")
   public static Method getInterfaceMethodIfPossible(Method method) {
      return getInterfaceMethodIfPossible(method, null);
   }

   public static Method getInterfaceMethodIfPossible(Method method, @Nullable Class<?> targetClass) {
      return getInterfaceMethodIfPossible(method, targetClass, false);
   }

   private static Method getInterfaceMethodIfPossible(Method method, @Nullable Class<?> targetClass, boolean requirePublicInterface) {
      Class<?> declaringClass = method.getDeclaringClass();
      if (Modifier.isPublic(method.getModifiers())
         && (!declaringClass.isInterface() || requirePublicInterface && !Modifier.isPublic(declaringClass.getModifiers()))) {
         String methodName = method.getName();
         Class<?>[] parameterTypes = method.getParameterTypes();
         Map<Method, Method> methodCache = requirePublicInterface ? publicInterfaceMethodCache : interfaceMethodCache;
         Method result = methodCache.computeIfAbsent(
            method, key -> findInterfaceMethodIfPossible(methodName, parameterTypes, declaringClass, Object.class, requirePublicInterface)
         );
         if (result == null && targetClass != null) {
            result = findInterfaceMethodIfPossible(methodName, parameterTypes, targetClass, declaringClass, requirePublicInterface);
         }

         return result != null ? result : method;
      } else {
         return method;
      }
   }

   private static @Nullable Method findInterfaceMethodIfPossible(
      String methodName, Class<?>[] parameterTypes, Class<?> startClass, Class<?> endClass, boolean requirePublicInterface
   ) {
      for (Class<?> current = startClass; current != null && current != endClass; current = current.getSuperclass()) {
         for (Class<?> ifc : current.getInterfaces()) {
            try {
               if (!requirePublicInterface || Modifier.isPublic(ifc.getModifiers())) {
                  return ifc.getMethod(methodName, parameterTypes);
               }
            } catch (NoSuchMethodException var11) {
            }
         }
      }

      return null;
   }

   public static Method getPubliclyAccessibleMethodIfPossible(Method method, @Nullable Class<?> targetClass) {
      Class<?> declaringClass = method.getDeclaringClass();
      if (Modifier.isPublic(method.getModifiers())
         && !Modifier.isStatic(method.getModifiers())
         && (
            !Modifier.isPublic(declaringClass.getModifiers())
               || !declaringClass.getModule().isExported(declaringClass.getPackageName(), ClassUtils.class.getModule())
         )) {
         Method interfaceMethod = getInterfaceMethodIfPossible(method, targetClass, true);
         if (interfaceMethod != method
            && interfaceMethod.getDeclaringClass().getModule().isExported(interfaceMethod.getDeclaringClass().getPackageName(), ClassUtils.class.getModule())) {
            return interfaceMethod;
         }

         if (declaringClass.getSuperclass() == Object.class && !ReflectionUtils.isObjectMethod(method)) {
            return method;
         }

         Method result = publiclyAccessibleMethodCache.computeIfAbsent(
            method, key -> findPubliclyAccessibleMethodIfPossible(key.getName(), key.getParameterTypes(), declaringClass)
         );
         return result != null ? result : method;
      } else {
         return method;
      }
   }

   private static @Nullable Method findPubliclyAccessibleMethodIfPossible(String methodName, Class<?>[] parameterTypes, Class<?> declaringClass) {
      Class<?> current = declaringClass.getSuperclass();

      while (current != null) {
         Method method = getMethodOrNull(current, methodName, parameterTypes);
         if (method == null) {
            break;
         }

         if (Modifier.isPublic(method.getDeclaringClass().getModifiers())
            && method.getDeclaringClass().getModule().isExported(method.getDeclaringClass().getPackageName(), ClassUtils.class.getModule())) {
            return method;
         }

         current = method.getDeclaringClass().getSuperclass();
      }

      return null;
   }

   public static boolean isUserLevelMethod(Method method) {
      Assert.notNull(method, "Method must not be null");
      return method.isBridge() || !method.isSynthetic() && !isGroovyObjectMethod(method);
   }

   private static boolean isGroovyObjectMethod(Method method) {
      return method.getDeclaringClass().getName().equals("groovy.lang.GroovyObject");
   }

   private static boolean isOverridable(Method method, @Nullable Class<?> targetClass) {
      if ((method.getModifiers() & 26) != 0) {
         return false;
      } else {
         return (method.getModifiers() & 5) != 0 ? true : targetClass == null || getPackageName(method.getDeclaringClass()).equals(getPackageName(targetClass));
      }
   }

   public static @Nullable Method getStaticMethod(Class<?> clazz, String methodName, Class<?>... args) {
      Assert.notNull(clazz, "Class must not be null");
      Assert.notNull(methodName, "Method name must not be null");

      try {
         Method method = clazz.getMethod(methodName, args);
         return Modifier.isStatic(method.getModifiers()) ? method : null;
      } catch (NoSuchMethodException ex) {
         return null;
      }
   }

   private static @Nullable Method getMethodOrNull(Class<?> clazz, String methodName, @Nullable Class<?> @Nullable [] paramTypes) {
      try {
         return clazz.getMethod(methodName, paramTypes);
      } catch (NoSuchMethodException ex) {
         return null;
      }
   }

   private static Set<Method> findMethodCandidatesByName(Class<?> clazz, String methodName) {
      Set<Method> candidates = new HashSet<>(1);
      Method[] methods = clazz.getMethods();

      for (Method method : methods) {
         if (methodName.equals(method.getName())) {
            candidates.add(method);
         }
      }

      return candidates;
   }

   static {
      primitiveWrapperTypeMap.put(Boolean.class, boolean.class);
      primitiveWrapperTypeMap.put(Byte.class, byte.class);
      primitiveWrapperTypeMap.put(Character.class, char.class);
      primitiveWrapperTypeMap.put(Double.class, double.class);
      primitiveWrapperTypeMap.put(Float.class, float.class);
      primitiveWrapperTypeMap.put(Integer.class, int.class);
      primitiveWrapperTypeMap.put(Long.class, long.class);
      primitiveWrapperTypeMap.put(Short.class, short.class);
      primitiveWrapperTypeMap.put(Void.class, void.class);

      for (Entry<Class<?>, Class<?>> entry : primitiveWrapperTypeMap.entrySet()) {
         primitiveTypeToWrapperMap.put(entry.getValue(), entry.getKey());
         registerCommonClasses(entry.getKey());
      }

      Set<Class<?>> primitiveTypes = new HashSet<>(32);
      primitiveTypes.addAll(primitiveWrapperTypeMap.values());
      Collections.addAll(primitiveTypes, boolean[].class, byte[].class, char[].class, double[].class, float[].class, int[].class, long[].class, short[].class);

      for (Class<?> primitiveType : primitiveTypes) {
         primitiveTypeNameMap.put(primitiveType.getName(), primitiveType);
      }

      registerCommonClasses(Boolean[].class, Byte[].class, Character[].class, Double[].class, Float[].class, Integer[].class, Long[].class, Short[].class);
      registerCommonClasses(Number.class, Number[].class, String.class, String[].class, Class.class, Class[].class, Object.class, Object[].class);
      registerCommonClasses(Throwable.class, Exception.class, RuntimeException.class, Error.class, StackTraceElement.class, StackTraceElement[].class);
      registerCommonClasses(
         Enum.class, Iterable.class, Iterator.class, Enumeration.class, Collection.class, List.class, Set.class, Map.class, Entry.class, Optional.class
      );
      Class<?>[] javaLanguageInterfaceArray = new Class[]{
         Serializable.class, Externalizable.class, Closeable.class, AutoCloseable.class, Cloneable.class, Comparable.class
      };
      registerCommonClasses(javaLanguageInterfaceArray);
      javaLanguageInterfaces = Set.of(javaLanguageInterfaceArray);
   }
}
