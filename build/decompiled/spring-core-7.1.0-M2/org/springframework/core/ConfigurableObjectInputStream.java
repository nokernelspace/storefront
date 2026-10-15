package org.springframework.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ClassUtils;

public class ConfigurableObjectInputStream extends ObjectInputStream {
   private final @Nullable ClassLoader classLoader;
   private final boolean acceptProxyClasses;

   public ConfigurableObjectInputStream(InputStream in, @Nullable ClassLoader classLoader) throws IOException {
      this(in, classLoader, true);
   }

   public ConfigurableObjectInputStream(InputStream in, @Nullable ClassLoader classLoader, boolean acceptProxyClasses) throws IOException {
      super(in);
      this.classLoader = classLoader;
      this.acceptProxyClasses = acceptProxyClasses;
   }

   @Override
   protected Class<?> resolveClass(ObjectStreamClass classDesc) throws IOException, ClassNotFoundException {
      try {
         return this.classLoader != null ? ClassUtils.forName(classDesc.getName(), this.classLoader) : super.resolveClass(classDesc);
      } catch (ClassNotFoundException ex) {
         return this.resolveFallbackIfPossible(classDesc.getName(), ex);
      }
   }

   @Override
   protected Class<?> resolveProxyClass(String[] interfaces) throws IOException, ClassNotFoundException {
      if (!this.acceptProxyClasses) {
         throw new NotSerializableException("Not allowed to accept serialized proxy classes");
      }

      if (this.classLoader == null) {
         try {
            return super.resolveProxyClass(interfaces);
         } catch (ClassNotFoundException var7) {
            ClassNotFoundException ex = var7;
            Class<?>[] resolvedInterfaces = new Class[interfaces.length];

            for (int i = 0; i < interfaces.length; i++) {
               resolvedInterfaces[i] = this.resolveFallbackIfPossible(interfaces[i], ex);
            }

            return ClassUtils.createCompositeInterface(resolvedInterfaces, this.getFallbackClassLoader());
         }
      } else {
         Class<?>[] resolvedInterfaces = new Class[interfaces.length];

         for (int i = 0; i < interfaces.length; i++) {
            try {
               resolvedInterfaces[i] = ClassUtils.forName(interfaces[i], this.classLoader);
            } catch (ClassNotFoundException ex) {
               resolvedInterfaces[i] = this.resolveFallbackIfPossible(interfaces[i], ex);
            }
         }

         try {
            return ClassUtils.createCompositeInterface(resolvedInterfaces, this.classLoader);
         } catch (IllegalArgumentException ex) {
            throw new ClassNotFoundException(null, ex);
         }
      }
   }

   protected Class<?> resolveFallbackIfPossible(String className, ClassNotFoundException ex) throws IOException, ClassNotFoundException {
      throw ex;
   }

   protected @Nullable ClassLoader getFallbackClassLoader() throws IOException {
      return null;
   }
}
