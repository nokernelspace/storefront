package org.springframework.context.support;

import java.nio.charset.Charset;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

public abstract class AbstractResourceBasedMessageSource extends AbstractMessageSource {
   private final Set<String> basenameSet = new LinkedHashSet<>(4);
   private @Nullable Charset defaultCharset;
   private boolean fallbackToSystemLocale = true;
   private @Nullable Locale defaultLocale;
   private long cacheMillis = -1L;

   public void setBasename(String basename) {
      this.setBasenames(basename);
   }

   public void setBasenames(String... basenames) {
      this.basenameSet.clear();
      this.addBasenames(basenames);
   }

   public void addBasenames(String... basenames) {
      if (!ObjectUtils.isEmpty(basenames)) {
         for (String basename : basenames) {
            Assert.hasText(basename, "Basename must not be empty");
            this.basenameSet.add(basename.trim());
         }
      }
   }

   public Set<String> getBasenameSet() {
      return this.basenameSet;
   }

   public void setDefaultEncoding(@Nullable String defaultEncoding) {
      this.defaultCharset = defaultEncoding != null ? Charset.forName(defaultEncoding) : null;
   }

   protected @Nullable String getDefaultEncoding() {
      return this.defaultCharset != null ? this.defaultCharset.name() : null;
   }

   public void setDefaultCharset(@Nullable Charset defaultCharset) {
      this.defaultCharset = defaultCharset;
   }

   protected @Nullable Charset getDefaultCharset() {
      return this.defaultCharset;
   }

   public void setFallbackToSystemLocale(boolean fallbackToSystemLocale) {
      this.fallbackToSystemLocale = fallbackToSystemLocale;
   }

   @Deprecated(since = "5.2.2")
   protected boolean isFallbackToSystemLocale() {
      return this.fallbackToSystemLocale;
   }

   public void setDefaultLocale(@Nullable Locale defaultLocale) {
      this.defaultLocale = defaultLocale;
   }

   protected @Nullable Locale getDefaultLocale() {
      if (this.defaultLocale != null) {
         return this.defaultLocale;
      } else {
         return this.fallbackToSystemLocale ? Locale.getDefault() : null;
      }
   }

   public void setCacheSeconds(int cacheSeconds) {
      this.cacheMillis = cacheSeconds * 1000L;
   }

   public void setCacheMillis(long cacheMillis) {
      this.cacheMillis = cacheMillis;
   }

   protected long getCacheMillis() {
      return this.cacheMillis;
   }
}
