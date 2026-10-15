package org.springframework.cache.annotation;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.CollectionUtils;
import org.springframework.util.function.SingletonSupplier;

@Configuration(proxyBeanMethods = false)
public abstract class AbstractCachingConfiguration implements ImportAware {
   protected @Nullable AnnotationAttributes enableCaching;
   protected @Nullable Supplier<@Nullable CacheManager> cacheManager;
   protected @Nullable Supplier<@Nullable CacheResolver> cacheResolver;
   protected @Nullable Supplier<@Nullable KeyGenerator> keyGenerator;
   protected @Nullable Supplier<@Nullable CacheErrorHandler> errorHandler;

   @Override
   public void setImportMetadata(AnnotationMetadata importMetadata) {
      this.enableCaching = AnnotationAttributes.fromMap(importMetadata.getAnnotationAttributes(EnableCaching.class.getName()));
      if (this.enableCaching == null) {
         throw new IllegalArgumentException("@EnableCaching is not present on importing class " + importMetadata.getClassName());
      }
   }

   @Autowired
   void setConfigurers(ObjectProvider<CachingConfigurer> configurers) {
      Supplier<CachingConfigurer> configurer = () -> {
         List<CachingConfigurer> candidates = configurers.stream().toList();
         if (CollectionUtils.isEmpty(candidates)) {
            return null;
         } else if (candidates.size() > 1) {
            throw new IllegalStateException(
               candidates.size()
                  + " implementations of CachingConfigurer were found when only 1 was expected. Refactor the configuration such that CachingConfigurer is implemented only once or not at all."
            );
         } else {
            return candidates.get(0);
         }
      };
      this.useCachingConfigurer(new AbstractCachingConfiguration.CachingConfigurerSupplier(configurer));
   }

   protected void useCachingConfigurer(AbstractCachingConfiguration.CachingConfigurerSupplier cachingConfigurerSupplier) {
      this.cacheManager = cachingConfigurerSupplier.adapt(CachingConfigurer::cacheManager);
      this.cacheResolver = cachingConfigurerSupplier.adapt(CachingConfigurer::cacheResolver);
      this.keyGenerator = cachingConfigurerSupplier.adapt(CachingConfigurer::keyGenerator);
      this.errorHandler = cachingConfigurerSupplier.adapt(CachingConfigurer::errorHandler);
   }

   protected static class CachingConfigurerSupplier {
      private final SingletonSupplier<@Nullable CachingConfigurer> supplier;

      public CachingConfigurerSupplier(Supplier<@Nullable CachingConfigurer> supplier) {
         this.supplier = SingletonSupplier.ofNullable(supplier);
      }

      public <T> Supplier<@Nullable T> adapt(Function<CachingConfigurer, @Nullable T> provider) {
         return () -> {
            CachingConfigurer cachingConfigurer = (CachingConfigurer)this.supplier.get();
            return cachingConfigurer != null ? provider.apply(cachingConfigurer) : null;
         };
      }
   }
}
