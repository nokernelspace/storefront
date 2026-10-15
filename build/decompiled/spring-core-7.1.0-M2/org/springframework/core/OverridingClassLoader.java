package org.springframework.core;

import java.io.IOException;
import java.io.InputStream;
import org.jspecify.annotations.Nullable;
import org.springframework.util.FileCopyUtils;

public class OverridingClassLoader extends DecoratingClassLoader {
   public static final String[] DEFAULT_EXCLUDED_PACKAGES = new String[]{"java.", "javax.", "sun.", "oracle.", "javassist.", "org.aspectj.", "net.sf.cglib."};
   private static final String CLASS_FILE_SUFFIX = ".class";
   private final @Nullable ClassLoader overrideDelegate;

   public OverridingClassLoader(@Nullable ClassLoader parent) {
      this(parent, null);
   }

   public OverridingClassLoader(@Nullable ClassLoader parent, @Nullable ClassLoader overrideDelegate) {
      super(parent);
      this.overrideDelegate = overrideDelegate;

      for (String packageName : DEFAULT_EXCLUDED_PACKAGES) {
         this.excludePackage(packageName);
      }
   }

   @Override
   public Class<?> loadClass(String name) throws ClassNotFoundException {
      return this.overrideDelegate != null && this.isEligibleForOverriding(name) ? this.overrideDelegate.loadClass(name) : super.loadClass(name);
   }

   @Override
   protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (this.isEligibleForOverriding(name)) {
         Class<?> result = this.loadClassForOverriding(name);
         if (result != null) {
            if (resolve) {
               this.resolveClass(result);
            }

            return result;
         }
      }

      return super.loadClass(name, resolve);
   }

   protected boolean isEligibleForOverriding(String className) {
      return !this.isExcluded(className);
   }

   protected @Nullable Class<?> loadClassForOverriding(String name) throws ClassNotFoundException {
      Class<?> result = this.findLoadedClass(name);
      if (result == null) {
         byte[] bytes = this.loadBytesForClass(name);
         if (bytes != null) {
            result = this.defineClass(name, bytes, 0, bytes.length);
         }
      }

      return result;
   }

   protected byte @Nullable [] loadBytesForClass(String name) throws ClassNotFoundException {
      InputStream is = this.openStreamForClass(name);
      if (is == null) {
         return null;
      }

      try {
         byte[] bytes = FileCopyUtils.copyToByteArray(is);
         return this.transformIfNecessary(name, bytes);
      } catch (IOException ex) {
         throw new ClassNotFoundException("Cannot load resource for class [" + name + "]", ex);
      }
   }

   protected @Nullable InputStream openStreamForClass(String name) {
      String internalName = name.replace('.', '/') + ".class";
      return this.getParent().getResourceAsStream(internalName);
   }

   protected byte[] transformIfNecessary(String name, byte[] bytes) {
      return bytes;
   }

   static {
      ClassLoader.registerAsParallelCapable();
   }
}
