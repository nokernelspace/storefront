package org.springframework.aot.nativex.feature;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

class ThrowawayClassLoader extends ClassLoader {
   private final ClassLoader resourceLoader;

   ThrowawayClassLoader(ClassLoader parent) {
      super(parent.getParent());
      this.resourceLoader = parent;
   }

   @Override
   protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      synchronized (this.getClassLoadingLock(name)) {
         Class<?> loaded = this.findLoadedClass(name);
         if (loaded != null) {
            return loaded;
         }

         Class var10000;
         try {
            var10000 = super.loadClass(name, true);
         } catch (ClassNotFoundException ex) {
            Class<?> loadedFromResource = this.loadClassFromResource(name);
            if (loadedFromResource == null) {
               throw ex;
            }

            return loadedFromResource;
         }

         return var10000;
      }
   }

   private Class<?> loadClassFromResource(String name) throws ClassNotFoundException, ClassFormatError {
      String resourceName = name.replace('.', '/') + ".class";
      InputStream inputStream = this.resourceLoader.getResourceAsStream(resourceName);
      if (inputStream == null) {
         return null;
      }

      try (inputStream) {
         ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
         inputStream.transferTo(outputStream);
         byte[] bytes = outputStream.toByteArray();
         return this.defineClass(name, bytes, 0, bytes.length);
      } catch (IOException ex) {
         throw new ClassNotFoundException("Cannot load resource for class [" + name + "]", ex);
      }
   }

   @Override
   protected URL findResource(String name) {
      return this.resourceLoader.getResource(name);
   }

   static {
      registerAsParallelCapable();
   }
}
