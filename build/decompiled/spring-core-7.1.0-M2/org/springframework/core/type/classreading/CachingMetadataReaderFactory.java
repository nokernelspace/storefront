package org.springframework.core.type.classreading;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

public class CachingMetadataReaderFactory extends AbstractMetadataReaderFactory {
   public static final int DEFAULT_CACHE_LIMIT = 256;
   private final MetadataReaderFactory delegate;
   private @Nullable Map<Resource, MetadataReader> metadataReaderCache;

   public CachingMetadataReaderFactory() {
      this(MetadataReaderFactory.create((ClassLoader)null));
   }

   public CachingMetadataReaderFactory(@Nullable ClassLoader classLoader) {
      this(MetadataReaderFactory.create(classLoader));
   }

   public CachingMetadataReaderFactory(@Nullable ResourceLoader resourceLoader) {
      this(MetadataReaderFactory.create(resourceLoader));
   }

   CachingMetadataReaderFactory(MetadataReaderFactory delegate) {
      super(delegate.getResourceLoader());
      this.delegate = delegate;
      if (this.getResourceLoader() instanceof DefaultResourceLoader defaultResourceLoader) {
         this.metadataReaderCache = defaultResourceLoader.getResourceCache(MetadataReader.class);
      } else {
         this.setCacheLimit(256);
      }
   }

   public void setCacheLimit(int cacheLimit) {
      if (cacheLimit <= 0) {
         this.metadataReaderCache = null;
      } else if (this.metadataReaderCache instanceof CachingMetadataReaderFactory.LocalResourceCache localResourceCache) {
         localResourceCache.setCacheLimit(cacheLimit);
      } else {
         this.metadataReaderCache = new CachingMetadataReaderFactory.LocalResourceCache(cacheLimit);
      }
   }

   public int getCacheLimit() {
      if (this.metadataReaderCache instanceof CachingMetadataReaderFactory.LocalResourceCache localResourceCache) {
         return localResourceCache.getCacheLimit();
      } else {
         return this.metadataReaderCache != null ? Integer.MAX_VALUE : 0;
      }
   }

   @Override
   public MetadataReader getMetadataReader(Resource resource) throws IOException {
      if (this.metadataReaderCache instanceof ConcurrentMap) {
         MetadataReader metadataReader = this.metadataReaderCache.get(resource);
         if (metadataReader == null) {
            metadataReader = this.delegate.getMetadataReader(resource);
            this.metadataReaderCache.put(resource, metadataReader);
         }

         return metadataReader;
      } else if (this.metadataReaderCache != null) {
         synchronized (this.metadataReaderCache) {
            MetadataReader metadataReader = this.metadataReaderCache.get(resource);
            if (metadataReader == null) {
               metadataReader = this.delegate.getMetadataReader(resource);
               this.metadataReaderCache.put(resource, metadataReader);
            }

            return metadataReader;
         }
      } else {
         return this.delegate.getMetadataReader(resource);
      }
   }

   public void clearCache() {
      if (this.metadataReaderCache instanceof CachingMetadataReaderFactory.LocalResourceCache) {
         synchronized (this.metadataReaderCache) {
            this.metadataReaderCache.clear();
         }
      } else if (this.metadataReaderCache != null) {
         this.setCacheLimit(256);
      }
   }

   private static class LocalResourceCache extends LinkedHashMap<Resource, MetadataReader> {
      private volatile int cacheLimit;

      public LocalResourceCache(int cacheLimit) {
         super(cacheLimit, 0.75F, true);
         this.cacheLimit = cacheLimit;
      }

      public void setCacheLimit(int cacheLimit) {
         this.cacheLimit = cacheLimit;
      }

      public int getCacheLimit() {
         return this.cacheLimit;
      }

      @Override
      protected boolean removeEldestEntry(Entry<Resource, MetadataReader> eldest) {
         return this.size() > this.cacheLimit;
      }
   }
}
