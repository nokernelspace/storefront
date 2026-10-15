package org.springframework.web.bind;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.core.CollectionFactory;
import org.springframework.validation.DataBinder;
import org.springframework.web.multipart.MultipartFile;

public class WebDataBinder extends DataBinder {
   public static final String DEFAULT_FIELD_MARKER_PREFIX = "_";
   public static final String DEFAULT_FIELD_DEFAULT_PREFIX = "!";
   private @Nullable String fieldMarkerPrefix = "_";
   private @Nullable String fieldDefaultPrefix = "!";
   private boolean bindEmptyMultipartFiles = true;

   public WebDataBinder(@Nullable Object target) {
      super(target);
   }

   public WebDataBinder(@Nullable Object target, String objectName) {
      super(target, objectName);
   }

   public void setFieldMarkerPrefix(@Nullable String fieldMarkerPrefix) {
      this.fieldMarkerPrefix = fieldMarkerPrefix;
   }

   public @Nullable String getFieldMarkerPrefix() {
      return this.fieldMarkerPrefix;
   }

   public void setFieldDefaultPrefix(@Nullable String fieldDefaultPrefix) {
      this.fieldDefaultPrefix = fieldDefaultPrefix;
   }

   public @Nullable String getFieldDefaultPrefix() {
      return this.fieldDefaultPrefix;
   }

   public void setBindEmptyMultipartFiles(boolean bindEmptyMultipartFiles) {
      this.bindEmptyMultipartFiles = bindEmptyMultipartFiles;
   }

   public boolean isBindEmptyMultipartFiles() {
      return this.bindEmptyMultipartFiles;
   }

   protected @Nullable Object resolvePrefixValue(String name, Class<?> type, BiFunction<String, Class<?>, @Nullable Object> resolver) {
      Object value = resolver.apply(name, type);
      if (value == null) {
         String prefix = this.getFieldDefaultPrefix();
         if (prefix != null) {
            value = resolver.apply(prefix + name, type);
         }

         if (value == null) {
            prefix = this.getFieldMarkerPrefix();
            if (prefix != null && resolver.apply(prefix + name, type) != null) {
               value = this.getEmptyValue(type);
            }
         }
      }

      return value;
   }

   protected void doBind(MutablePropertyValues mpvs) {
      this.checkFieldDefaults(mpvs);
      this.checkFieldMarkers(mpvs);
      this.adaptEmptyArrayIndices(mpvs);
      super.doBind(mpvs);
   }

   protected void checkFieldDefaults(MutablePropertyValues mpvs) {
      String fieldDefaultPrefix = this.getFieldDefaultPrefix();
      if (fieldDefaultPrefix != null) {
         PropertyValue[] pvArray = mpvs.getPropertyValues();

         for (PropertyValue pv : pvArray) {
            if (pv.getName().startsWith(fieldDefaultPrefix)) {
               String field = pv.getName().substring(fieldDefaultPrefix.length());
               if (this.isAllowed(field) && this.getPropertyAccessor().isWritableProperty(field) && !mpvs.contains(field)) {
                  mpvs.add(field, pv.getValue());
               }

               mpvs.removePropertyValue(pv);
            }
         }
      }
   }

   protected void checkFieldMarkers(MutablePropertyValues mpvs) {
      String fieldMarkerPrefix = this.getFieldMarkerPrefix();
      if (fieldMarkerPrefix != null) {
         PropertyValue[] pvArray = mpvs.getPropertyValues();

         for (PropertyValue pv : pvArray) {
            if (pv.getName().startsWith(fieldMarkerPrefix)) {
               String field = pv.getName().substring(fieldMarkerPrefix.length());
               if (this.isAllowed(field) && this.getPropertyAccessor().isWritableProperty(field) && !mpvs.contains(field)) {
                  Class<?> fieldType = this.getPropertyAccessor().getPropertyType(field);
                  mpvs.add(field, this.getEmptyValue(field, fieldType));
               }

               mpvs.removePropertyValue(pv);
            }
         }
      }
   }

   protected void adaptEmptyArrayIndices(MutablePropertyValues mpvs) {
      for (PropertyValue pv : mpvs.getPropertyValues()) {
         String name = pv.getName();
         if (name.endsWith("[]")) {
            String field = name.substring(0, name.length() - 2);
            if (this.isAllowed(field) && this.getPropertyAccessor().isWritableProperty(field) && !mpvs.contains(field)) {
               mpvs.add(field, pv.getValue());
            }

            mpvs.removePropertyValue(pv);
         }
      }
   }

   protected @Nullable Object getEmptyValue(String field, @Nullable Class<?> fieldType) {
      return fieldType != null ? this.getEmptyValue(fieldType) : null;
   }

   public @Nullable Object getEmptyValue(Class<?> fieldType) {
      try {
         if (boolean.class == fieldType || Boolean.class == fieldType) {
            return Boolean.FALSE;
         }

         if (fieldType.isArray()) {
            return Array.newInstance(fieldType.componentType(), 0);
         }

         if (Collection.class.isAssignableFrom(fieldType)) {
            return CollectionFactory.createCollection(fieldType, 0);
         }

         if (Map.class.isAssignableFrom(fieldType)) {
            return CollectionFactory.createMap(fieldType, 0);
         }
      } catch (IllegalArgumentException ex) {
         if (logger.isDebugEnabled()) {
            logger.debug("Failed to create default value - falling back to null: " + ex.getMessage());
         }
      }

      return null;
   }

   protected void bindMultipart(Map<String, List<MultipartFile>> multipartFiles, MutablePropertyValues mpvs) {
      multipartFiles.forEach((key, values) -> {
         if (values.size() == 1) {
            MultipartFile value = values.get(0);
            if (this.isBindEmptyMultipartFiles() || !value.isEmpty()) {
               mpvs.add(key, value);
            }
         } else {
            mpvs.add(key, values);
         }
      });
   }
}
