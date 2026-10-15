package org.springframework.core.io;

import java.beans.PropertyEditorSupport;
import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class ResourceEditor extends PropertyEditorSupport {
   private final ResourceLoader resourceLoader;
   private @Nullable PropertyResolver propertyResolver;
   private final boolean ignoreUnresolvablePlaceholders;

   public ResourceEditor() {
      this(new DefaultResourceLoader(), null);
   }

   public ResourceEditor(ResourceLoader resourceLoader, @Nullable PropertyResolver propertyResolver) {
      this(resourceLoader, propertyResolver, true);
   }

   public ResourceEditor(ResourceLoader resourceLoader, @Nullable PropertyResolver propertyResolver, boolean ignoreUnresolvablePlaceholders) {
      Assert.notNull(resourceLoader, "ResourceLoader must not be null");
      this.resourceLoader = resourceLoader;
      this.propertyResolver = propertyResolver;
      this.ignoreUnresolvablePlaceholders = ignoreUnresolvablePlaceholders;
   }

   @Override
   public void setAsText(String text) {
      if (StringUtils.hasText(text)) {
         String locationToUse = this.resolvePath(text).trim();
         this.setValue(this.resourceLoader.getResource(locationToUse));
      } else {
         this.setValue(null);
      }
   }

   protected String resolvePath(String path) {
      if (this.propertyResolver == null) {
         this.propertyResolver = new StandardEnvironment();
      }

      return this.ignoreUnresolvablePlaceholders ? this.propertyResolver.resolvePlaceholders(path) : this.propertyResolver.resolveRequiredPlaceholders(path);
   }

   @Override
   public @Nullable String getAsText() {
      Resource value = (Resource)this.getValue();

      try {
         return value != null ? value.getURL().toExternalForm() : "";
      } catch (IOException ex) {
         return null;
      }
   }
}
