package org.springframework.core.io.support;

import java.beans.PropertyEditorSupport;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class ResourceArrayPropertyEditor extends PropertyEditorSupport {
   private static final Log logger = LogFactory.getLog(ResourceArrayPropertyEditor.class);
   private final ResourcePatternResolver resourcePatternResolver;
   private @Nullable PropertyResolver propertyResolver;
   private final boolean ignoreUnresolvablePlaceholders;

   public ResourceArrayPropertyEditor() {
      this(new PathMatchingResourcePatternResolver(), null, true);
   }

   public ResourceArrayPropertyEditor(ResourcePatternResolver resourcePatternResolver, @Nullable PropertyResolver propertyResolver) {
      this(resourcePatternResolver, propertyResolver, true);
   }

   public ResourceArrayPropertyEditor(
      ResourcePatternResolver resourcePatternResolver, @Nullable PropertyResolver propertyResolver, boolean ignoreUnresolvablePlaceholders
   ) {
      Assert.notNull(resourcePatternResolver, "ResourcePatternResolver must not be null");
      this.resourcePatternResolver = resourcePatternResolver;
      this.propertyResolver = propertyResolver;
      this.ignoreUnresolvablePlaceholders = ignoreUnresolvablePlaceholders;
   }

   @Override
   public void setAsText(String text) {
      String pattern = this.resolvePath(text).trim();
      String[] locationPatterns = StringUtils.commaDelimitedListToStringArray(pattern);
      if (locationPatterns.length == 1) {
         this.setValue(this.getResources(locationPatterns[0]));
      } else {
         Resource[] resources = Arrays.stream(locationPatterns).map(String::trim).map(this::getResources).flatMap(Arrays::stream).toArray(Resource[]::new);
         this.setValue(resources);
      }
   }

   private Resource[] getResources(String locationPattern) {
      try {
         return this.resourcePatternResolver.getResources(locationPattern);
      } catch (IOException ex) {
         throw new IllegalArgumentException("Could not resolve resource location pattern [" + locationPattern + "]: " + ex.getMessage());
      }
   }

   @Override
   public void setValue(Object value) throws IllegalArgumentException {
      if (value instanceof Collection || value instanceof Object[] && !(value instanceof Resource[])) {
         Collection<?> input = value instanceof Collection<?> collection ? collection : Arrays.asList((Object[])value);
         Set<Resource> merged = new LinkedHashSet<>();

         for (Object element : input) {
            if (element instanceof String path) {
               String pattern = this.resolvePath(path.trim());

               try {
                  Resource[] resources = this.resourcePatternResolver.getResources(pattern);
                  Collections.addAll(merged, resources);
               } catch (IOException ex) {
                  if (logger.isDebugEnabled()) {
                     logger.debug("Could not retrieve resources for pattern '" + pattern + "'", ex);
                  }
               }
            } else {
               if (!(element instanceof Resource resource)) {
                  throw new IllegalArgumentException(
                     "Cannot convert element [" + element + "] to [" + Resource.class.getName() + "]: only location String and Resource object supported"
                  );
               }

               merged.add(resource);
            }
         }

         super.setValue(merged.toArray(new Resource[0]));
      } else {
         super.setValue(value);
      }
   }

   protected String resolvePath(String path) {
      if (this.propertyResolver == null) {
         this.propertyResolver = new StandardEnvironment();
      }

      return this.ignoreUnresolvablePlaceholders ? this.propertyResolver.resolvePlaceholders(path) : this.propertyResolver.resolveRequiredPlaceholders(path);
   }
}
