package org.springframework.context.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.ResourceBundle.Control;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

public class ResourceBundleMessageSource extends AbstractResourceBasedMessageSource implements BeanClassLoaderAware {
   private @Nullable ClassLoader bundleClassLoader;
   private @Nullable ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();
   private final Map<String, Map<Locale, ResourceBundle>> cachedResourceBundles = new ConcurrentHashMap<>();
   private final Map<ResourceBundle, Map<String, Map<Locale, MessageFormat>>> cachedBundleMessageFormats = new ConcurrentHashMap<>();
   private volatile ResourceBundleMessageSource.@Nullable MessageSourceControl control = new ResourceBundleMessageSource.MessageSourceControl();

   public ResourceBundleMessageSource() {
      this.setDefaultCharset(StandardCharsets.ISO_8859_1);
   }

   public void setBundleClassLoader(ClassLoader classLoader) {
      this.bundleClassLoader = classLoader;
   }

   protected @Nullable ClassLoader getBundleClassLoader() {
      return this.bundleClassLoader != null ? this.bundleClassLoader : this.beanClassLoader;
   }

   public void setBeanClassLoader(ClassLoader classLoader) {
      this.beanClassLoader = classLoader;
   }

   @Override
   protected @Nullable String resolveCodeWithoutArguments(String code, Locale locale) {
      for (String basename : this.getBasenameSet()) {
         ResourceBundle bundle = this.getResourceBundle(basename, locale);
         if (bundle != null) {
            String result = this.getStringOrNull(bundle, code);
            if (result != null) {
               return result;
            }
         }
      }

      return null;
   }

   @Override
   protected @Nullable MessageFormat resolveCode(String code, Locale locale) {
      for (String basename : this.getBasenameSet()) {
         ResourceBundle bundle = this.getResourceBundle(basename, locale);
         if (bundle != null) {
            MessageFormat messageFormat = this.getMessageFormat(bundle, code, locale);
            if (messageFormat != null) {
               return messageFormat;
            }
         }
      }

      return null;
   }

   protected @Nullable ResourceBundle getResourceBundle(String basename, Locale locale) {
      if (this.getCacheMillis() < 0L && JVM_LOCALES.contains(locale)) {
         Map<Locale, ResourceBundle> localeMap = this.cachedResourceBundles.get(basename);
         if (localeMap != null) {
            ResourceBundle bundle = localeMap.get(locale);
            if (bundle != null) {
               return bundle;
            }
         }

         try {
            ResourceBundle bundle = this.doGetBundle(basename, locale);
            if (localeMap == null) {
               localeMap = this.cachedResourceBundles.computeIfAbsent(basename, bn -> new ConcurrentHashMap<>());
            }

            localeMap.put(locale, bundle);
            return bundle;
         } catch (MissingResourceException ex) {
            if (this.logger.isWarnEnabled()) {
               this.logger.warn("ResourceBundle [" + basename + "] not found for MessageSource: " + ex.getMessage());
            }

            return null;
         }
      } else {
         return this.doGetBundle(basename, locale);
      }
   }

   protected ResourceBundle doGetBundle(String basename, Locale locale) throws MissingResourceException {
      ClassLoader classLoader = this.getBundleClassLoader();
      Assert.state(classLoader != null, "No bundle ClassLoader set");
      ResourceBundleMessageSource.MessageSourceControl control = this.control;
      if (control != null) {
         try {
            return ResourceBundle.getBundle(basename, locale, classLoader, control);
         } catch (UnsupportedOperationException ex) {
            this.control = null;
            Charset charset = this.getDefaultCharset();
            if (charset != null && this.logger.isInfoEnabled()) {
               this.logger
                  .info(
                     "ResourceBundleMessageSource is configured to read resources with encoding '"
                        + charset
                        + "' but ResourceBundle.Control is not supported in current system environment: "
                        + ex.getMessage()
                        + " - falling back to plain ResourceBundle.getBundle retrieval with the platform default encoding. Consider setting the 'defaultCharset' property to 'null' for participating in the platform default and therefore avoiding this log message."
                  );
            }
         }
      }

      return ResourceBundle.getBundle(basename, locale, classLoader);
   }

   protected ResourceBundle loadBundle(Reader reader) throws IOException {
      return new PropertyResourceBundle(reader);
   }

   protected ResourceBundle loadBundle(InputStream inputStream) throws IOException {
      return new PropertyResourceBundle(inputStream);
   }

   protected @Nullable MessageFormat getMessageFormat(ResourceBundle bundle, String code, Locale locale) throws MissingResourceException {
      if (!JVM_LOCALES.contains(locale)) {
         String msg = this.getStringOrNull(bundle, code);
         return msg != null ? this.createMessageFormat(msg, locale) : null;
      }

      Map<String, Map<Locale, MessageFormat>> codeMap = this.cachedBundleMessageFormats.get(bundle);
      Map<Locale, MessageFormat> localeMap = null;
      if (codeMap != null) {
         localeMap = codeMap.get(code);
         if (localeMap != null) {
            MessageFormat result = localeMap.get(locale);
            if (result != null) {
               return result;
            }
         }
      }

      String msg = this.getStringOrNull(bundle, code);
      if (msg != null) {
         if (codeMap == null) {
            codeMap = this.cachedBundleMessageFormats.computeIfAbsent(bundle, b -> new ConcurrentHashMap<>());
         }

         if (localeMap == null) {
            localeMap = codeMap.computeIfAbsent(code, c -> new ConcurrentHashMap<>());
         }

         MessageFormat result = this.createMessageFormat(msg, locale);
         localeMap.put(locale, result);
         return result;
      } else {
         return null;
      }
   }

   protected @Nullable String getStringOrNull(ResourceBundle bundle, String key) {
      if (bundle.containsKey(key)) {
         try {
            return bundle.getString(key);
         } catch (MissingResourceException var4) {
         }
      }

      return null;
   }

   @Override
   public String toString() {
      return this.getClass().getName() + ": basenames=" + this.getBasenameSet();
   }

   private class MessageSourceControl extends Control {
      @Override
      public @Nullable ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader, boolean reload) throws IllegalAccessException, InstantiationException, IOException {
         if (format.equals("java.properties")) {
            String bundleName = this.toBundleName(baseName, locale);
            String resourceName = this.toResourceName(bundleName, "properties");
            ClassLoader classLoader = loader;
            boolean reloadFlag = reload;
            InputStream inputStream = null;
            if (reloadFlag) {
               URL url = classLoader.getResource(resourceName);
               if (url != null) {
                  URLConnection connection = url.openConnection();
                  if (connection != null) {
                     connection.setUseCaches(false);
                     inputStream = connection.getInputStream();
                  }
               }
            } else {
               inputStream = classLoader.getResourceAsStream(resourceName);
            }

            if (inputStream == null) {
               return null;
            }

            Charset charset = ResourceBundleMessageSource.this.getDefaultCharset();
            if (charset != null) {
               try (InputStreamReader bundleReader = new InputStreamReader(inputStream, charset)) {
                  return ResourceBundleMessageSource.this.loadBundle(bundleReader);
               }
            } else {
               try (InputStream bundleStream = inputStream) {
                  return ResourceBundleMessageSource.this.loadBundle(bundleStream);
               }
            }
         } else {
            return super.newBundle(baseName, locale, format, loader, reload);
         }
      }

      @Override
      public @Nullable Locale getFallbackLocale(String baseName, Locale locale) {
         Locale defaultLocale = ResourceBundleMessageSource.this.getDefaultLocale();
         return defaultLocale != null && !defaultLocale.equals(locale) ? defaultLocale : null;
      }

      @Override
      public long getTimeToLive(String baseName, Locale locale) {
         long cacheMillis = ResourceBundleMessageSource.this.getCacheMillis();
         return cacheMillis >= 0L ? cacheMillis : super.getTimeToLive(baseName, locale);
      }

      @Override
      public boolean needsReload(String baseName, Locale locale, String format, ClassLoader loader, ResourceBundle bundle, long loadTime) {
         if (super.needsReload(baseName, locale, format, loader, bundle, loadTime)) {
            ResourceBundleMessageSource.this.cachedBundleMessageFormats.remove(bundle);
            return true;
         } else {
            return false;
         }
      }
   }
}
