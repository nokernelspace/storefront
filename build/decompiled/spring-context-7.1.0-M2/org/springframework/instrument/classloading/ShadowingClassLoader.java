package org.springframework.instrument.classloading;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.DecoratingClassLoader;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;

public class ShadowingClassLoader extends DecoratingClassLoader {
   public static final String[] DEFAULT_EXCLUDED_PACKAGES = new String[]{
      "java.",
      "javax.",
      "jdk.",
      "sun.",
      "oracle.",
      "com.sun.",
      "com.ibm.",
      "COM.ibm.",
      "org.w3c.",
      "org.xml.",
      "org.dom4j.",
      "org.eclipse",
      "org.aspectj.",
      "net.sf.cglib",
      "org.springframework.cglib",
      "org.apache.xerces.",
      "org.apache.commons.logging."
   };
   private final ClassLoader enclosingClassLoader;
   private final List<ClassFileTransformer> classFileTransformers = new ArrayList<>(1);
   private final Map<String, Class<?>> classCache = new HashMap<>();

   public ShadowingClassLoader(ClassLoader enclosingClassLoader) {
      this(enclosingClassLoader, true);
   }

   public ShadowingClassLoader(ClassLoader enclosingClassLoader, boolean defaultExcludes) {
      Assert.notNull(enclosingClassLoader, "Enclosing ClassLoader must not be null");
      this.enclosingClassLoader = enclosingClassLoader;
      if (defaultExcludes) {
         for (String excludedPackage : DEFAULT_EXCLUDED_PACKAGES) {
            this.excludePackage(excludedPackage);
         }
      }
   }

   public void addTransformer(ClassFileTransformer transformer) {
      Assert.notNull(transformer, "Transformer must not be null");
      this.classFileTransformers.add(transformer);
   }

   public void copyTransformers(ShadowingClassLoader other) {
      Assert.notNull(other, "Other ClassLoader must not be null");
      this.classFileTransformers.addAll(other.classFileTransformers);
   }

   public Class<?> loadClass(String name) throws ClassNotFoundException {
      if (this.shouldShadow(name)) {
         Class<?> cls = this.classCache.get(name);
         return cls != null ? cls : this.doLoadClass(name);
      } else {
         return this.enclosingClassLoader.loadClass(name);
      }
   }

   private boolean shouldShadow(String className) {
      return !className.equals(this.getClass().getName()) && !className.endsWith("ShadowingClassLoader") && this.isEligibleForShadowing(className);
   }

   protected boolean isEligibleForShadowing(String className) {
      return !this.isExcluded(className);
   }

   private Class<?> doLoadClass(String name) throws ClassNotFoundException {
      String internalName = StringUtils.replace(name, ".", "/") + ".class";
      InputStream is = this.enclosingClassLoader.getResourceAsStream(internalName);
      if (is == null) {
         throw new ClassNotFoundException(name);
      }

      try {
         byte[] bytes = FileCopyUtils.copyToByteArray(is);
         bytes = this.applyTransformers(name, bytes);
         Class<?> cls = this.defineClass(name, bytes, 0, bytes.length);
         if (cls.getPackage() == null) {
            int packageSeparator = name.lastIndexOf(46);
            if (packageSeparator != -1) {
               String packageName = name.substring(0, packageSeparator);
               this.definePackage(packageName, null, null, null, null, null, null, null);
            }
         }

         this.classCache.put(name, cls);
         return cls;
      } catch (IOException ex) {
         throw new ClassNotFoundException("Cannot load resource for class [" + name + "]", ex);
      }
   }

   private byte[] applyTransformers(String name, byte[] bytes) {
      String internalName = StringUtils.replace(name, ".", "/");

      try {
         for (ClassFileTransformer transformer : this.classFileTransformers) {
            byte[] transformed = transformer.transform(this, internalName, null, null, bytes);
            bytes = transformed != null ? transformed : bytes;
         }

         return bytes;
      } catch (IllegalClassFormatException ex) {
         throw new IllegalStateException(ex);
      }
   }

   public URL getResource(String name) {
      return this.enclosingClassLoader.getResource(name);
   }

   public @Nullable InputStream getResourceAsStream(String name) {
      return this.enclosingClassLoader.getResourceAsStream(name);
   }

   public Enumeration<URL> getResources(String name) throws IOException {
      return this.enclosingClassLoader.getResources(name);
   }
}
