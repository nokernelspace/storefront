package org.springframework.core;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public abstract class DecoratingClassLoader extends ClassLoader {
   private final Set<String> excludedPackages = ConcurrentHashMap.newKeySet(8);
   private final Set<String> excludedClasses = ConcurrentHashMap.newKeySet(8);

   public DecoratingClassLoader() {
   }

   public DecoratingClassLoader(@Nullable ClassLoader parent) {
      super(parent);
   }

   public void excludePackage(String packageName) {
      Assert.notNull(packageName, "Package name must not be null");
      this.excludedPackages.add(packageName);
   }

   public void excludeClass(String className) {
      Assert.notNull(className, "Class name must not be null");
      this.excludedClasses.add(className);
   }

   protected boolean isExcluded(String className) {
      if (this.excludedClasses.contains(className)) {
         return true;
      }

      for (String packageName : this.excludedPackages) {
         if (className.startsWith(packageName)) {
            return true;
         }
      }

      return false;
   }

   static {
      ClassLoader.registerAsParallelCapable();
   }
}
