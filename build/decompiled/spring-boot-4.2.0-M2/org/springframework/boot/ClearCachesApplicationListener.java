package org.springframework.boot;

import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.util.ReflectionUtils;

class ClearCachesApplicationListener implements ApplicationListener<ContextRefreshedEvent> {
   public void onApplicationEvent(ContextRefreshedEvent event) {
      ReflectionUtils.clearCache();
      this.clearClassLoaderCaches(Thread.currentThread().getContextClassLoader());
   }

   private void clearClassLoaderCaches(@Nullable ClassLoader classLoader) {
      if (classLoader != null) {
         try {
            Method clearCacheMethod = classLoader.getClass().getDeclaredMethod("clearCache");
            clearCacheMethod.invoke(classLoader);
         } catch (Exception var3) {
         }

         this.clearClassLoaderCaches(classLoader.getParent());
      }
   }
}
