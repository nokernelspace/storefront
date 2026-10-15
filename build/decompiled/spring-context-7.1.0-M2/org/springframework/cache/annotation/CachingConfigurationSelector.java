package org.springframework.cache.annotation;

import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.AdviceModeImportSelector;
import org.springframework.context.annotation.AutoProxyRegistrar;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

public class CachingConfigurationSelector extends AdviceModeImportSelector<EnableCaching> {
   private static final String PROXY_JCACHE_CONFIGURATION_CLASS = "org.springframework.cache.jcache.config.ProxyJCacheConfiguration";
   private static final String CACHE_ASPECT_CONFIGURATION_CLASS_NAME = "org.springframework.cache.aspectj.AspectJCachingConfiguration";
   private static final String JCACHE_ASPECT_CONFIGURATION_CLASS_NAME = "org.springframework.cache.aspectj.AspectJJCacheConfiguration";
   private static final boolean JSR_107_PRESENT;
   private static final boolean JCACHE_IMPL_PRESENT;

   @Override
   public String[] selectImports(AdviceMode adviceMode) {
      return switch (adviceMode) {
         case PROXY -> this.getProxyImports();
         case ASPECTJ -> this.getAspectJImports();
      };
   }

   private String[] getProxyImports() {
      List<String> result = new ArrayList<>(3);
      result.add(AutoProxyRegistrar.class.getName());
      result.add(ProxyCachingConfiguration.class.getName());
      if (JSR_107_PRESENT && JCACHE_IMPL_PRESENT) {
         result.add("org.springframework.cache.jcache.config.ProxyJCacheConfiguration");
      }

      return StringUtils.toStringArray(result);
   }

   private String[] getAspectJImports() {
      List<String> result = new ArrayList<>(2);
      result.add("org.springframework.cache.aspectj.AspectJCachingConfiguration");
      if (JSR_107_PRESENT && JCACHE_IMPL_PRESENT) {
         result.add("org.springframework.cache.aspectj.AspectJJCacheConfiguration");
      }

      return StringUtils.toStringArray(result);
   }

   static {
      ClassLoader classLoader = CachingConfigurationSelector.class.getClassLoader();
      JSR_107_PRESENT = ClassUtils.isPresent("javax.cache.Cache", classLoader);
      JCACHE_IMPL_PRESENT = ClassUtils.isPresent("org.springframework.cache.jcache.config.ProxyJCacheConfiguration", classLoader);
   }
}
