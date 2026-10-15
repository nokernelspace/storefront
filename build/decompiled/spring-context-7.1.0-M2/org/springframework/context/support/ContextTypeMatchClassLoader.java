package org.springframework.context.support;

import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.DecoratingClassLoader;
import org.springframework.core.OverridingClassLoader;
import org.springframework.core.SmartClassLoader;
import org.springframework.util.ReflectionUtils;

class ContextTypeMatchClassLoader extends DecoratingClassLoader implements SmartClassLoader {
   private static final @Nullable Method findLoadedClassMethod;
   private final Map<String, byte[]> bytesCache = new ConcurrentHashMap<>(256);

   public ContextTypeMatchClassLoader(@Nullable ClassLoader parent) {
      super(parent);
   }

   public Class<?> loadClass(String name) throws ClassNotFoundException {
      return new ContextTypeMatchClassLoader.ContextOverridingClassLoader(this.getParent()).loadClass(name);
   }

   public boolean isClassReloadable(Class<?> clazz) {
      return clazz.getClassLoader() instanceof ContextTypeMatchClassLoader.ContextOverridingClassLoader;
   }

   public Class<?> publicDefineClass(String name, byte[] b, @Nullable ProtectionDomain protectionDomain) {
      return this.defineClass(name, b, 0, b.length, protectionDomain);
   }

   static {
      ClassLoader.registerAsParallelCapable();

      Method method;
      try {
         method = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
         ReflectionUtils.makeAccessible(method);
      } catch (Throwable ex) {
         method = null;
         LogFactory.getLog(ContextTypeMatchClassLoader.class).debug("ClassLoader.findLoadedClass not accessible -> will always override requested class", ex);
      }

      findLoadedClassMethod = method;
   }

   private class ContextOverridingClassLoader extends OverridingClassLoader {
      public ContextOverridingClassLoader(ClassLoader parent) {
         super(parent);
      }

      protected boolean isEligibleForOverriding(String className) {
         if (!this.isExcluded(className) && !ContextTypeMatchClassLoader.this.isExcluded(className)) {
            if (ContextTypeMatchClassLoader.findLoadedClassMethod != null) {
               for (ClassLoader parent = this.getParent(); parent != null; parent = parent.getParent()) {
                  if (ReflectionUtils.invokeMethod(ContextTypeMatchClassLoader.findLoadedClassMethod, parent, new Object[]{className}) != null) {
                     return false;
                  }
               }
            }

            return true;
         } else {
            return false;
         }
      }

      protected @Nullable Class<?> loadClassForOverriding(String name) throws ClassNotFoundException {
         byte[] bytes = ContextTypeMatchClassLoader.this.bytesCache.get(name);
         if (bytes == null) {
            bytes = this.loadBytesForClass(name);
            if (bytes == null) {
               return null;
            }

            ContextTypeMatchClassLoader.this.bytesCache.put(name, bytes);
         }

         return this.defineClass(name, bytes, 0, bytes.length);
      }
   }
}
