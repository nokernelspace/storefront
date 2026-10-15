package org.springframework.beans.factory.config;

import java.util.Map;
import java.util.Map.Entry;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.TypeConverter;
import org.springframework.core.ResolvableType;
import org.springframework.util.CollectionUtils;

public class MapFactoryBean extends AbstractFactoryBean<Map<Object, Object>> {
   private @Nullable Map<?, ?> sourceMap;
   private @Nullable Class<? extends Map> targetMapClass;

   public void setSourceMap(Map<?, ?> sourceMap) {
      this.sourceMap = sourceMap;
   }

   public void setTargetMapClass(@Nullable Class<? extends Map> targetMapClass) {
      if (targetMapClass == null) {
         throw new IllegalArgumentException("'targetMapClass' must not be null");
      }

      if (!Map.class.isAssignableFrom(targetMapClass)) {
         throw new IllegalArgumentException("'targetMapClass' must implement [java.util.Map]");
      }

      this.targetMapClass = targetMapClass;
   }

   @Override
   public Class<Map> getObjectType() {
      return Map.class;
   }

   protected Map<Object, Object> createInstance() {
      if (this.sourceMap == null) {
         throw new IllegalArgumentException("'sourceMap' is required");
      }

      Map<Object, Object> result = null;
      if (this.targetMapClass != null) {
         result = BeanUtils.instantiateClass((Class<Map<Object, Object>>)this.targetMapClass);
      } else {
         result = CollectionUtils.newLinkedHashMap(this.sourceMap.size());
      }

      Class<?> keyType = null;
      Class<?> valueType = null;
      if (this.targetMapClass != null) {
         ResolvableType mapType = ResolvableType.forClass(this.targetMapClass).asMap();
         keyType = mapType.resolveGeneric(new int[]{0});
         valueType = mapType.resolveGeneric(new int[]{1});
      }

      if (keyType == null && valueType == null) {
         result.putAll((Map<? extends Object, ? extends Object>)this.sourceMap);
      } else {
         TypeConverter converter = this.getBeanTypeConverter();

         for (Entry<?, ?> entry : this.sourceMap.entrySet()) {
            Object convertedKey = converter.convertIfNecessary(entry.getKey(), keyType);
            Object convertedValue = converter.convertIfNecessary(entry.getValue(), valueType);
            result.put(convertedKey, convertedValue);
         }
      }

      return result;
   }
}
