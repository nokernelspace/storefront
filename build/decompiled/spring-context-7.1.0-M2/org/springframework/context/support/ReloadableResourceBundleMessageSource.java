package org.springframework.context.support;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ConcurrentLruCache;
import org.springframework.util.DefaultPropertiesPersister;
import org.springframework.util.PropertiesPersister;
import org.springframework.util.StringUtils;

public class ReloadableResourceBundleMessageSource extends AbstractResourceBasedMessageSource implements ResourceLoaderAware {
   private static final String PROPERTIES_EXTENSION = ".properties";
   private static final String XML_EXTENSION = ".xml";
   private List<String> fileExtensions = List.of(".properties", ".xml");
   private @Nullable Properties fileEncodings;
   private boolean concurrentRefresh = true;
   private PropertiesPersister propertiesPersister = DefaultPropertiesPersister.INSTANCE;
   private ResourceLoader resourceLoader = new DefaultResourceLoader();
   private final ConcurrentMap<String, Map<Locale, List<String>>> cachedFilenames = new ConcurrentHashMap<>();
   private final ConcurrentMap<String, ReloadableResourceBundleMessageSource.PropertiesHolder> cachedProperties = new ConcurrentHashMap<>();
   private final ConcurrentMap<Locale, ReloadableResourceBundleMessageSource.PropertiesHolder> cachedMergedProperties = new ConcurrentHashMap<>();
   private final ConcurrentLruCache<Locale, ReloadableResourceBundleMessageSource.PropertiesHolder> customLocaleProperties = new ConcurrentLruCache(
      64, locale -> this.mergeProperties(this.collectPropertiesToMerge(locale))
   );

   public void setFileExtensions(List<String> fileExtensions) {
      Assert.isTrue(!CollectionUtils.isEmpty(fileExtensions), "At least one file extension is required");

      for (String extension : fileExtensions) {
         if (!extension.startsWith(".")) {
            throw new IllegalArgumentException("File extension '" + extension + "' should start with '.'");
         }
      }

      this.fileExtensions = Collections.unmodifiableList(fileExtensions);
   }

   public void setFileEncodings(Properties fileEncodings) {
      this.fileEncodings = fileEncodings;
   }

   public void setConcurrentRefresh(boolean concurrentRefresh) {
      this.concurrentRefresh = concurrentRefresh;
   }

   public void setPropertiesPersister(@Nullable PropertiesPersister propertiesPersister) {
      this.propertiesPersister = (PropertiesPersister)(propertiesPersister != null ? propertiesPersister : DefaultPropertiesPersister.INSTANCE);
   }

   @Override
   public void setResourceLoader(@Nullable ResourceLoader resourceLoader) {
      this.resourceLoader = (ResourceLoader)(resourceLoader != null ? resourceLoader : new DefaultResourceLoader());
   }

   @Override
   protected @Nullable String resolveCodeWithoutArguments(String code, Locale locale) {
      if (this.getCacheMillis() < 0L) {
         ReloadableResourceBundleMessageSource.PropertiesHolder propHolder = this.getMergedProperties(locale);
         return propHolder.getProperty(code);
      }

      for (String basename : this.getBasenameSet()) {
         for (String filename : this.calculateAllFilenames(basename, locale)) {
            ReloadableResourceBundleMessageSource.PropertiesHolder propHolder = this.getProperties(filename, locale);
            String result = propHolder.getProperty(code);
            if (result != null) {
               return result;
            }
         }
      }

      return null;
   }

   @Override
   protected @Nullable MessageFormat resolveCode(String code, Locale locale) {
      if (this.getCacheMillis() < 0L) {
         ReloadableResourceBundleMessageSource.PropertiesHolder propHolder = this.getMergedProperties(locale);
         return propHolder.getMessageFormat(code, locale);
      }

      for (String basename : this.getBasenameSet()) {
         for (String filename : this.calculateAllFilenames(basename, locale)) {
            ReloadableResourceBundleMessageSource.PropertiesHolder propHolder = this.getProperties(filename, locale);
            MessageFormat result = propHolder.getMessageFormat(code, locale);
            if (result != null) {
               return result;
            }
         }
      }

      return null;
   }

   protected ReloadableResourceBundleMessageSource.PropertiesHolder getMergedProperties(Locale locale) {
      ReloadableResourceBundleMessageSource.PropertiesHolder mergedHolder = this.cachedMergedProperties.get(locale);
      if (mergedHolder != null) {
         return mergedHolder;
      }

      if (JVM_LOCALES.contains(locale)) {
         mergedHolder = this.mergeProperties(this.collectPropertiesToMerge(locale));
         ReloadableResourceBundleMessageSource.PropertiesHolder existing = this.cachedMergedProperties.putIfAbsent(locale, mergedHolder);
         if (existing != null) {
            mergedHolder = existing;
         }

         return mergedHolder;
      } else {
         return (ReloadableResourceBundleMessageSource.PropertiesHolder)this.customLocaleProperties.get(locale);
      }
   }

   protected List<ReloadableResourceBundleMessageSource.PropertiesHolder> collectPropertiesToMerge(Locale locale) {
      String[] basenames = StringUtils.toStringArray(this.getBasenameSet());
      List<ReloadableResourceBundleMessageSource.PropertiesHolder> holders = new ArrayList<>(basenames.length);

      for (int i = basenames.length - 1; i >= 0; i--) {
         List<String> filenames = this.calculateAllFilenames(basenames[i], locale);

         for (int j = filenames.size() - 1; j >= 0; j--) {
            String filename = filenames.get(j);
            ReloadableResourceBundleMessageSource.PropertiesHolder propHolder = this.getProperties(filename, locale);
            if (propHolder.getProperties() != null) {
               holders.add(propHolder);
            }
         }
      }

      return holders;
   }

   protected ReloadableResourceBundleMessageSource.PropertiesHolder mergeProperties(List<ReloadableResourceBundleMessageSource.PropertiesHolder> holders) {
      Properties mergedProps = this.newProperties();
      long latestTimestamp = -1L;

      for (ReloadableResourceBundleMessageSource.PropertiesHolder holder : holders) {
         mergedProps.putAll(holder.getProperties());
         if (holder.getFileTimestamp() > latestTimestamp) {
            latestTimestamp = holder.getFileTimestamp();
         }
      }

      return new ReloadableResourceBundleMessageSource.PropertiesHolder(mergedProps, latestTimestamp);
   }

   protected List<String> calculateAllFilenames(String basename, Locale locale) {
      Map<Locale, List<String>> localeMap = this.cachedFilenames.get(basename);
      if (localeMap != null) {
         List<String> filenames = localeMap.get(locale);
         if (filenames != null) {
            return filenames;
         }
      }

      List<String> filenames = new ArrayList<>(7);
      filenames.addAll(this.calculateFilenamesForLocale(basename, locale));
      Locale defaultLocale = this.getDefaultLocale();
      if (defaultLocale != null && !defaultLocale.equals(locale)) {
         for (String fallbackFilename : this.calculateFilenamesForLocale(basename, defaultLocale)) {
            if (!filenames.contains(fallbackFilename)) {
               filenames.add(fallbackFilename);
            }
         }
      }

      filenames.add(basename);
      if (JVM_LOCALES.contains(locale)) {
         if (localeMap == null) {
            localeMap = new ConcurrentHashMap<>();
            Map<Locale, List<String>> existing = this.cachedFilenames.putIfAbsent(basename, localeMap);
            if (existing != null) {
               localeMap = existing;
            }
         }

         localeMap.put(locale, filenames);
      }

      return filenames;
   }

   protected List<String> calculateFilenamesForLocale(String basename, Locale locale) {
      List<String> result = new ArrayList<>(3);
      String language = locale.getLanguage();
      String country = locale.getCountry();
      String variant = locale.getVariant();
      StringBuilder temp = new StringBuilder(basename);
      temp.append('_');
      if (!language.isEmpty()) {
         temp.append(language);
         result.add(0, temp.toString());
      }

      temp.append('_');
      if (!country.isEmpty()) {
         temp.append(country);
         result.add(0, temp.toString());
      }

      if (!variant.isEmpty() && (!language.isEmpty() || !country.isEmpty())) {
         temp.append('_').append(variant);
         result.add(0, temp.toString());
      }

      return result;
   }

   private ReloadableResourceBundleMessageSource.PropertiesHolder getProperties(String filename, Locale locale) {
      ReloadableResourceBundleMessageSource.PropertiesHolder propHolder = this.getProperties(filename);
      if (propHolder.getProperties() == null && !JVM_LOCALES.contains(locale)) {
         this.cachedProperties.remove(filename);
      }

      return propHolder;
   }

   protected ReloadableResourceBundleMessageSource.PropertiesHolder getProperties(String filename) {
      ReloadableResourceBundleMessageSource.PropertiesHolder propHolder = this.cachedProperties.get(filename);
      long originalTimestamp = -2L;
      if (propHolder != null) {
         originalTimestamp = propHolder.getRefreshTimestamp();
         if (originalTimestamp == -1L || originalTimestamp > System.currentTimeMillis() - this.getCacheMillis()) {
            return propHolder;
         }
      } else {
         propHolder = new ReloadableResourceBundleMessageSource.PropertiesHolder();
         ReloadableResourceBundleMessageSource.PropertiesHolder existingHolder = this.cachedProperties.putIfAbsent(filename, propHolder);
         if (existingHolder != null) {
            propHolder = existingHolder;
         }
      }

      if (this.concurrentRefresh && propHolder.getRefreshTimestamp() >= 0L) {
         if (!propHolder.refreshLock.tryLock()) {
            return propHolder;
         }
      } else {
         propHolder.refreshLock.lock();
      }

      try {
         ReloadableResourceBundleMessageSource.PropertiesHolder existingHolder = this.cachedProperties.get(filename);
         return existingHolder != null && existingHolder.getRefreshTimestamp() > originalTimestamp
            ? existingHolder
            : this.refreshProperties(filename, propHolder);
      } finally {
         propHolder.refreshLock.unlock();
      }
   }

   protected ReloadableResourceBundleMessageSource.PropertiesHolder refreshProperties(
      String filename, ReloadableResourceBundleMessageSource.@Nullable PropertiesHolder propHolder
   ) {
      long refreshTimestamp = this.getCacheMillis() < 0L ? -1L : System.currentTimeMillis();
      Resource resource = this.resolveResource(filename);
      if (resource != null) {
         long fileTimestamp = -1L;
         if (this.getCacheMillis() >= 0L) {
            try {
               fileTimestamp = resource.lastModified();
               if (propHolder != null && propHolder.getFileTimestamp() == fileTimestamp) {
                  if (this.logger.isDebugEnabled()) {
                     this.logger.debug("Re-caching properties for filename [" + filename + "] - file hasn't been modified");
                  }

                  propHolder.setRefreshTimestamp(refreshTimestamp);
                  return propHolder;
               }
            } catch (IOException ex) {
               if (this.logger.isDebugEnabled()) {
                  this.logger.debug(resource + " could not be resolved in the file system - assuming that it hasn't changed", ex);
               }

               fileTimestamp = -1L;
            }
         }

         try {
            Properties props = this.loadProperties(resource, filename);
            propHolder = new ReloadableResourceBundleMessageSource.PropertiesHolder(props, fileTimestamp);
         } catch (IOException ex) {
            if (this.logger.isWarnEnabled()) {
               this.logger.warn("Could not parse properties file [" + resource.getFilename() + "]", ex);
            }

            propHolder = new ReloadableResourceBundleMessageSource.PropertiesHolder();
         }
      } else {
         if (this.logger.isDebugEnabled()) {
            this.logger.debug("No properties file found for [" + filename + "]");
         }

         propHolder = new ReloadableResourceBundleMessageSource.PropertiesHolder();
      }

      propHolder.setRefreshTimestamp(refreshTimestamp);
      this.cachedProperties.put(filename, propHolder);
      return propHolder;
   }

   protected @Nullable Resource resolveResource(String filename) {
      for (String fileExtension : this.fileExtensions) {
         Resource resource = this.resourceLoader.getResource(filename + fileExtension);
         if (resource.exists()) {
            return resource;
         }
      }

      return null;
   }

   protected Properties loadProperties(Resource resource, String filename) throws IOException {
      Properties props = this.newProperties();
      String resourceFilename = resource.getFilename();
      resource.consumeContent(inputStream -> {
         if (resourceFilename != null && resourceFilename.endsWith(".xml")) {
            if (this.logger.isDebugEnabled()) {
               this.logger.debug("Loading properties [" + resource.getFilename() + "]");
            }

            this.propertiesPersister.loadFromXml(props, inputStream);
         } else {
            Charset charset = null;
            if (this.fileEncodings != null) {
               String charsetName = this.fileEncodings.getProperty(filename);
               if (charsetName != null) {
                  charset = Charset.forName(charsetName);
               }
            }

            if (charset == null) {
               charset = this.getDefaultCharset();
            }

            if (charset != null) {
               if (this.logger.isDebugEnabled()) {
                  this.logger.debug("Loading properties [" + resource.getFilename() + "] with encoding '" + charset + "'");
               }

               this.propertiesPersister.load(props, new InputStreamReader(inputStream, charset));
            } else {
               if (this.logger.isDebugEnabled()) {
                  this.logger.debug("Loading properties [" + resource.getFilename() + "]");
               }

               this.propertiesPersister.load(props, inputStream);
            }
         }
      });
      return props;
   }

   protected Properties newProperties() {
      return new Properties();
   }

   public void clearCache() {
      this.logger.debug("Clearing entire resource bundle cache");
      this.cachedProperties.clear();
      this.cachedMergedProperties.clear();
   }

   public void clearCacheIncludingAncestors() {
      this.clearCache();
      if (this.getParentMessageSource() instanceof ReloadableResourceBundleMessageSource reloadableMsgSrc) {
         reloadableMsgSrc.clearCacheIncludingAncestors();
      }
   }

   @Override
   public String toString() {
      return this.getClass().getName() + ": basenames=" + this.getBasenameSet();
   }

   protected class PropertiesHolder {
      private final @Nullable Properties properties;
      private final long fileTimestamp;
      private volatile long refreshTimestamp = -2L;
      private final Lock refreshLock = new ReentrantLock();
      private final ConcurrentMap<String, Map<Locale, MessageFormat>> cachedMessageFormats = new ConcurrentHashMap<>();

      public PropertiesHolder() {
         this.properties = null;
         this.fileTimestamp = -1L;
      }

      public PropertiesHolder(Properties properties, long fileTimestamp) {
         this.properties = properties;
         this.fileTimestamp = fileTimestamp;
      }

      public @Nullable Properties getProperties() {
         return this.properties;
      }

      public long getFileTimestamp() {
         return this.fileTimestamp;
      }

      public void setRefreshTimestamp(long refreshTimestamp) {
         this.refreshTimestamp = refreshTimestamp;
      }

      public long getRefreshTimestamp() {
         return this.refreshTimestamp;
      }

      public @Nullable String getProperty(String code) {
         return this.properties == null ? null : this.properties.getProperty(code);
      }

      public @Nullable MessageFormat getMessageFormat(String code, Locale locale) {
         if (this.properties == null) {
            return null;
         }

         Map<Locale, MessageFormat> localeMap = this.cachedMessageFormats.get(code);
         if (localeMap != null) {
            MessageFormat result = localeMap.get(locale);
            if (result != null) {
               return result;
            }
         }

         String msg = this.properties.getProperty(code);
         if (msg != null) {
            if (localeMap == null) {
               localeMap = new ConcurrentHashMap<>();
               Map<Locale, MessageFormat> existing = this.cachedMessageFormats.putIfAbsent(code, localeMap);
               if (existing != null) {
                  localeMap = existing;
               }
            }

            MessageFormat result = ReloadableResourceBundleMessageSource.this.createMessageFormat(msg, locale);
            localeMap.put(locale, result);
            return result;
         } else {
            return null;
         }
      }
   }
}
