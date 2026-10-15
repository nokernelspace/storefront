package org.springframework.cglib.core;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Constructor;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.springframework.asm.Type;

public class ReflectUtils {
   private static final Map primitives = new HashMap(8);
   private static final Map transforms = new HashMap(8);
   private static final ClassLoader defaultLoader = ReflectUtils.class.getClassLoader();
   private static final Method classLoaderDefineClassMethod;
   private static final Throwable THROWABLE;
   private static final ProtectionDomain PROTECTION_DOMAIN;
   private static final List<Method> OBJECT_METHODS = new ArrayList<>();
   private static BiConsumer<String, byte[]> generatedClassHandler;
   private static Consumer<Class<?>> loadedClassHandler;
   private static final String[] CGLIB_PACKAGES;

   private ReflectUtils() {
   }

   public static ProtectionDomain getProtectionDomain(final Class source) {
      return source == null ? null : source.getProtectionDomain();
   }

   public static Type[] getExceptionTypes(Member member) {
      if (member instanceof Method method) {
         return TypeUtils.getTypes(method.getExceptionTypes());
      } else if (member instanceof Constructor<?> constructor) {
         return TypeUtils.getTypes(constructor.getExceptionTypes());
      } else {
         throw new IllegalArgumentException("Cannot get exception types of a field");
      }
   }

   public static Signature getSignature(Member member) {
      if (member instanceof Method method) {
         return new Signature(member.getName(), Type.getMethodDescriptor(method));
      } else if (member instanceof Constructor<?> constructor) {
         Type[] types = TypeUtils.getTypes(constructor.getParameterTypes());
         return new Signature("<init>", Type.getMethodDescriptor(Type.VOID_TYPE, types));
      } else {
         throw new IllegalArgumentException("Cannot get signature of a field");
      }
   }

   public static Constructor findConstructor(String desc) {
      return findConstructor(desc, defaultLoader);
   }

   public static Constructor findConstructor(String desc, ClassLoader loader) {
      try {
         int lparen = desc.indexOf(40);
         String className = desc.substring(0, lparen).trim();
         return getClass(className, loader).getConstructor(parseTypes(desc, loader));
      } catch (ClassNotFoundException | NoSuchMethodException ex) {
         throw new CodeGenerationException(ex);
      }
   }

   public static Method findMethod(String desc) {
      return findMethod(desc, defaultLoader);
   }

   public static Method findMethod(String desc, ClassLoader loader) {
      try {
         int lparen = desc.indexOf(40);
         int dot = desc.lastIndexOf(46, lparen);
         String className = desc.substring(0, dot).trim();
         String methodName = desc.substring(dot + 1, lparen).trim();
         return getClass(className, loader).getDeclaredMethod(methodName, parseTypes(desc, loader));
      } catch (ClassNotFoundException | NoSuchMethodException ex) {
         throw new CodeGenerationException(ex);
      }
   }

   private static Class[] parseTypes(String desc, ClassLoader loader) throws ClassNotFoundException {
      int lparen = desc.indexOf(40);
      int rparen = desc.indexOf(41, lparen);
      List params = new ArrayList();
      int start = lparen + 1;

      while (true) {
         int comma = desc.indexOf(44, start);
         if (comma < 0) {
            if (start < rparen) {
               params.add(desc.substring(start, rparen).trim());
            }

            Class[] types = new Class[params.size()];

            for (int i = 0; i < types.length; i++) {
               types[i] = getClass((String)params.get(i), loader);
            }

            return types;
         }

         params.add(desc.substring(start, comma).trim());
         start = comma + 1;
      }
   }

   private static Class getClass(String className, ClassLoader loader) throws ClassNotFoundException {
      return getClass(className, loader, CGLIB_PACKAGES);
   }

   private static Class getClass(String className, ClassLoader loader, String[] packages) throws ClassNotFoundException {
      String save = className;
      int dimensions = 0;
      int index = 0;

      while ((index = className.indexOf("[]", index) + 1) > 0) {
         dimensions++;
      }

      StringBuilder brackets = new StringBuilder(className.length() - dimensions);

      for (int i = 0; i < dimensions; i++) {
         brackets.append('[');
      }

      className = className.substring(0, className.length() - 2 * dimensions);
      String prefix = dimensions > 0 ? brackets + "L" : "";
      String suffix = dimensions > 0 ? ";" : "";

      try {
         return Class.forName(prefix + className + suffix, false, loader);
      } catch (ClassNotFoundException var16) {
         for (String pkg : packages) {
            try {
               return Class.forName(prefix + pkg + "." + className + suffix, false, loader);
            } catch (ClassNotFoundException var15) {
            }
         }

         if (dimensions == 0) {
            Class c = (Class)primitives.get(className);
            if (c != null) {
               return c;
            }
         } else {
            String transform = (String)transforms.get(className);
            if (transform != null) {
               try {
                  return Class.forName(brackets + transform, false, loader);
               } catch (ClassNotFoundException var14) {
               }
            }
         }

         throw new ClassNotFoundException(save);
      }
   }

   public static Object newInstance(Class type) {
      return newInstance(type, Constants.EMPTY_CLASS_ARRAY, null);
   }

   public static Object newInstance(Class type, Class[] parameterTypes, Object[] args) {
      return newInstance(getConstructor(type, parameterTypes), args);
   }

   public static Object newInstance(final Constructor cstruct, final Object[] args) {
      boolean flag = cstruct.isAccessible();

      try {
         if (!flag) {
            cstruct.setAccessible(true);
         }

         return cstruct.newInstance(args);
      } catch (InstantiationException | IllegalAccessException e) {
         throw new CodeGenerationException(e);
      } catch (InvocationTargetException e) {
         throw new CodeGenerationException(e.getTargetException());
      } finally {
         if (!flag) {
            cstruct.setAccessible(flag);
         }
      }
   }

   public static Constructor getConstructor(Class type, Class[] parameterTypes) {
      try {
         Constructor constructor = type.getDeclaredConstructor(parameterTypes);
         constructor.setAccessible(true);
         return constructor;
      } catch (NoSuchMethodException e) {
         throw new CodeGenerationException(e);
      }
   }

   public static String[] getNames(Class[] classes) {
      if (classes == null) {
         return null;
      }

      String[] names = new String[classes.length];

      for (int i = 0; i < names.length; i++) {
         names[i] = classes[i].getName();
      }

      return names;
   }

   public static Class[] getClasses(Object[] objects) {
      Class[] classes = new Class[objects.length];

      for (int i = 0; i < objects.length; i++) {
         classes[i] = objects[i].getClass();
      }

      return classes;
   }

   public static Method findNewInstance(Class iface) {
      Method m = findInterfaceMethod(iface);
      if (!m.getName().equals("newInstance")) {
         throw new IllegalArgumentException(iface + " missing newInstance method");
      } else {
         return m;
      }
   }

   public static Method[] getPropertyMethods(PropertyDescriptor[] properties, boolean read, boolean write) {
      Set methods = new HashSet();

      for (PropertyDescriptor pd : properties) {
         if (read) {
            methods.add(pd.getReadMethod());
         }

         if (write) {
            methods.add(pd.getWriteMethod());
         }
      }

      methods.remove(null);
      return methods.toArray(new Method[methods.size()]);
   }

   public static PropertyDescriptor[] getBeanProperties(Class type) {
      return getPropertiesHelper(type, true, true);
   }

   public static PropertyDescriptor[] getBeanGetters(Class type) {
      return getPropertiesHelper(type, true, false);
   }

   public static PropertyDescriptor[] getBeanSetters(Class type) {
      return getPropertiesHelper(type, false, true);
   }

   private static PropertyDescriptor[] getPropertiesHelper(Class type, boolean read, boolean write) {
      try {
         BeanInfo info = Introspector.getBeanInfo(type, Object.class);
         PropertyDescriptor[] all = info.getPropertyDescriptors();
         if (read && write) {
            return all;
         }

         List properties = new ArrayList(all.length);

         for (PropertyDescriptor pd : all) {
            if (read && pd.getReadMethod() != null || write && pd.getWriteMethod() != null) {
               properties.add(pd);
            }
         }

         return properties.toArray(new PropertyDescriptor[properties.size()]);
      } catch (IntrospectionException e) {
         throw new CodeGenerationException(e);
      }
   }

   public static Method findDeclaredMethod(final Class type, final String methodName, final Class[] parameterTypes) throws NoSuchMethodException {
      for (Class cl = type; cl != null; cl = cl.getSuperclass()) {
         try {
            return cl.getDeclaredMethod(methodName, parameterTypes);
         } catch (NoSuchMethodException e) {
         }
      }

      throw new NoSuchMethodException(methodName);
   }

   public static List addAllMethods(final Class type, final List list) {
      if (type == Object.class) {
         list.addAll(OBJECT_METHODS);
      } else {
         list.addAll(Arrays.asList(type.getDeclaredMethods()));
      }

      Class superclass = type.getSuperclass();
      if (superclass != null) {
         addAllMethods(superclass, list);
      }

      Class[] interfaces = type.getInterfaces();

      for (Class element : interfaces) {
         addAllMethods(element, list);
      }

      return list;
   }

   public static List addAllInterfaces(Class type, List list) {
      Class superclass = type.getSuperclass();
      if (superclass != null) {
         list.addAll(Arrays.asList(type.getInterfaces()));
         addAllInterfaces(superclass, list);
      }

      return list;
   }

   public static Method findInterfaceMethod(Class iface) {
      if (!iface.isInterface()) {
         throw new IllegalArgumentException(iface + " is not an interface");
      } else {
         Method[] methods = iface.getDeclaredMethods();
         if (methods.length != 1) {
            throw new IllegalArgumentException("expecting exactly 1 method in " + iface);
         } else {
            return methods[0];
         }
      }
   }

   public static void setGeneratedClassHandler(BiConsumer<String, byte[]> handler) {
      generatedClassHandler = handler;
   }

   public static Class defineClass(String className, byte[] b, ClassLoader loader) throws Exception {
      return defineClass(className, b, loader, null, null);
   }

   public static Class defineClass(String className, byte[] b, ClassLoader loader, ProtectionDomain protectionDomain) throws Exception {
      return defineClass(className, b, loader, protectionDomain, null);
   }

   public static Class defineClass(String className, byte[] b, ClassLoader loader, ProtectionDomain protectionDomain, Class<?> contextClass) throws Exception {
      Class c = null;
      Throwable t = THROWABLE;
      BiConsumer<String, byte[]> handlerToUse = generatedClassHandler;
      if (handlerToUse != null) {
         handlerToUse.accept(className, b);
      }

      if (contextClass != null && contextClass.getClassLoader() == loader) {
         try {
            Lookup lookup = MethodHandles.privateLookupIn(contextClass, MethodHandles.lookup());
            c = lookup.defineClass(b);
         } catch (LinkageError | IllegalArgumentException ex) {
            if (ex instanceof LinkageError) {
               try {
                  c = contextClass.getClassLoader().loadClass(className);
               } catch (ClassNotFoundException var14) {
               }
            }

            if (c == null) {
               t = ex;
            }
         } catch (Throwable ex) {
            throw new CodeGenerationException(ex);
         }
      }

      if (c == null) {
         if (protectionDomain == null) {
            protectionDomain = PROTECTION_DOMAIN;
         }

         try {
            Method publicDefineClass = loader.getClass().getMethod("publicDefineClass", String.class, byte[].class, ProtectionDomain.class);
            c = (Class)publicDefineClass.invoke(loader, className, b, protectionDomain);
         } catch (InvocationTargetException ex) {
            if (!(ex.getTargetException() instanceof UnsupportedOperationException)) {
               throw new CodeGenerationException(ex.getTargetException());
            }

            t = ex.getTargetException();
         } catch (Throwable ex) {
            t = ex;
         }

         if (c == null && classLoaderDefineClassMethod != null) {
            Object[] args = new Object[]{className, b, 0, b.length, protectionDomain};

            try {
               if (!classLoaderDefineClassMethod.isAccessible()) {
                  classLoaderDefineClassMethod.setAccessible(true);
               }

               c = (Class)classLoaderDefineClassMethod.invoke(loader, args);
            } catch (InvocationTargetException ex) {
               throw new CodeGenerationException(ex.getTargetException());
            } catch (InaccessibleObjectException ex) {
               t = ex;
            } catch (Throwable ex) {
               throw new CodeGenerationException(ex);
            }
         }
      }

      if (c == null && contextClass != null && contextClass.getClassLoader() != loader) {
         try {
            Lookup lookup = MethodHandles.privateLookupIn(contextClass, MethodHandles.lookup());
            c = lookup.defineClass(b);
         } catch (LinkageError | IllegalAccessException ex) {
            if (ex instanceof LinkageError) {
               try {
                  c = contextClass.getClassLoader().loadClass(className);
               } catch (ClassNotFoundException var10) {
               }
            }

            if (c == null) {
               throw new CodeGenerationException(ex) {
                  @Override
                  public String getMessage() {
                     return "ClassLoader mismatch for ["
                        + contextClass.getName()
                        + "]: JVM should be started with --add-opens=java.base/java.lang=ALL-UNNAMED for ClassLoader.defineClass to be accessible on "
                        + loader.getClass().getName()
                        + "; consider co-locating the affected class in that target ClassLoader instead.";
                  }
               };
            }
         } catch (Throwable ex) {
            throw new CodeGenerationException(ex);
         }
      }

      if (c == null) {
         throw new CodeGenerationException(t) {
            @Override
            public String getMessage() {
               return "No compatible defineClass mechanism detected: JVM should be started with --add-opens=java.base/java.lang=ALL-UNNAMED for ClassLoader.defineClass to be accessible. On the module path, you may not be able to define this CGLIB-generated class at all.";
            }
         };
      }

      Class.forName(className, true, loader);
      return c;
   }

   public static void setLoadedClassHandler(Consumer<Class<?>> loadedClassHandler) {
      ReflectUtils.loadedClassHandler = loadedClassHandler;
   }

   public static Class<?> loadClass(String className, ClassLoader classLoader) throws ClassNotFoundException {
      Class<?> clazz = Class.forName(className, true, classLoader);
      Consumer<Class<?>> handlerToUse = loadedClassHandler;
      if (handlerToUse != null) {
         handlerToUse.accept(clazz);
      }

      return clazz;
   }

   public static int findPackageProtected(Class[] classes) {
      for (int i = 0; i < classes.length; i++) {
         if (!Modifier.isPublic(classes[i].getModifiers())) {
            return i;
         }
      }

      return 0;
   }

   public static MethodInfo getMethodInfo(final Member member, final int modifiers) {
      final Signature sig = getSignature(member);
      return new MethodInfo() {
         private ClassInfo ci;

         @Override
         public ClassInfo getClassInfo() {
            if (this.ci == null) {
               this.ci = ReflectUtils.getClassInfo(member.getDeclaringClass());
            }

            return this.ci;
         }

         @Override
         public int getModifiers() {
            return modifiers;
         }

         @Override
         public Signature getSignature() {
            return sig;
         }

         @Override
         public Type[] getExceptionTypes() {
            return ReflectUtils.getExceptionTypes(member);
         }
      };
   }

   public static MethodInfo getMethodInfo(Member member) {
      return getMethodInfo(member, member.getModifiers());
   }

   public static ClassInfo getClassInfo(final Class clazz) {
      final Type type = Type.getType(clazz);
      final Type sc = clazz.getSuperclass() == null ? null : Type.getType(clazz.getSuperclass());
      return new ClassInfo() {
         @Override
         public Type getType() {
            return type;
         }

         @Override
         public Type getSuperType() {
            return sc;
         }

         @Override
         public Type[] getInterfaces() {
            return TypeUtils.getTypes(clazz.getInterfaces());
         }

         @Override
         public int getModifiers() {
            return clazz.getModifiers();
         }
      };
   }

   public static Method[] findMethods(String[] namesAndDescriptors, Method[] methods) {
      Map map = new HashMap();

      for (Method method : methods) {
         map.put(method.getName() + Type.getMethodDescriptor(method), method);
      }

      Method[] result = new Method[namesAndDescriptors.length / 2];

      for (int i = 0; i < result.length; i++) {
         result[i] = (Method)map.get(namesAndDescriptors[i * 2] + namesAndDescriptors[i * 2 + 1]);
         if (result[i] == null) {
         }
      }

      return result;
   }

   static {
      Throwable throwable = null;

      Method classLoaderDefineClass;
      try {
         classLoaderDefineClass = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class, ProtectionDomain.class);
      } catch (Throwable t) {
         classLoaderDefineClass = null;
         throwable = t;
      }

      classLoaderDefineClassMethod = classLoaderDefineClass;
      THROWABLE = throwable;
      PROTECTION_DOMAIN = getProtectionDomain(ReflectUtils.class);

      for (Method method : Object.class.getDeclaredMethods()) {
         if (!"finalize".equals(method.getName()) && (method.getModifiers() & 24) <= 0) {
            OBJECT_METHODS.add(method);
         }
      }

      CGLIB_PACKAGES = new String[]{"java.lang"};
      primitives.put("byte", byte.class);
      primitives.put("char", char.class);
      primitives.put("double", double.class);
      primitives.put("float", float.class);
      primitives.put("int", int.class);
      primitives.put("long", long.class);
      primitives.put("short", short.class);
      primitives.put("boolean", boolean.class);
      transforms.put("byte", "B");
      transforms.put("char", "C");
      transforms.put("double", "D");
      transforms.put("float", "F");
      transforms.put("int", "I");
      transforms.put("long", "J");
      transforms.put("short", "S");
      transforms.put("boolean", "Z");
   }
}
