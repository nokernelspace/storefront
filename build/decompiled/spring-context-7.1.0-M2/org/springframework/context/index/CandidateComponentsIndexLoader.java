package org.springframework.context.index;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.SpringProperties;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.util.ConcurrentReferenceHashMap;

public final class CandidateComponentsIndexLoader {
   public static final String COMPONENTS_RESOURCE_LOCATION = "META-INF/spring.components";
   public static final String IGNORE_INDEX = "spring.index.ignore";
   private static final boolean shouldIgnoreIndex = SpringProperties.getFlag("spring.index.ignore");
   private static final Log logger = LogFactory.getLog(CandidateComponentsIndexLoader.class);
   private static final ConcurrentMap<ClassLoader, CandidateComponentsIndex> cache = new ConcurrentReferenceHashMap();

   private CandidateComponentsIndexLoader() {
   }

   public static @Nullable CandidateComponentsIndex loadIndex(@Nullable ClassLoader classLoader) {
      ClassLoader classLoaderToUse = classLoader;
      if (classLoaderToUse == null) {
         classLoaderToUse = CandidateComponentsIndexLoader.class.getClassLoader();
      }

      return cache.computeIfAbsent(classLoaderToUse, CandidateComponentsIndexLoader::doLoadIndex);
   }

   private static @Nullable CandidateComponentsIndex doLoadIndex(ClassLoader classLoader) {
      if (shouldIgnoreIndex) {
         return null;
      }

      try {
         Enumeration<URL> urls = classLoader.getResources("META-INF/spring.components");
         if (!urls.hasMoreElements()) {
            return null;
         }

         List<Properties> result = new ArrayList<>();

         while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            Properties properties = PropertiesLoaderUtils.loadProperties(new UrlResource(url));
            result.add(properties);
         }

         if (logger.isDebugEnabled()) {
            logger.debug("Loaded " + result.size() + " index(es)");
         }

         int totalCount = result.stream().mapToInt(Properties::size).sum();
         return totalCount > 0 ? new CandidateComponentsIndex(result) : null;
      } catch (IOException ex) {
         throw new IllegalStateException("Unable to load indexes from location [META-INF/spring.components]", ex);
      }
   }

   public static void addIndex(ClassLoader classLoader, CandidateComponentsIndex index) {
      cache.put(classLoader, index);
   }

   public static void clearCache() {
      cache.clear();
   }
}
