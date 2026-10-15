package org.springframework.boot.context.properties.bind;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.source.ConfigurationProperty;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertyState;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.core.CollectionFactory;
import org.springframework.core.ResolvableType;

class MapBinder extends AggregateBinder<Map<Object, Object>> {
   private static final Bindable<Map<String, String>> STRING_STRING_MAP = Bindable.mapOf(String.class, String.class);

   MapBinder(Binder.Context context) {
      super(context);
   }

   @Override
   protected boolean isAllowRecursiveBinding(@Nullable ConfigurationPropertySource source) {
      return true;
   }

   @Override
   protected @Nullable Object bindAggregate(ConfigurationPropertyName name, Bindable<?> target, AggregateElementBinder elementBinder) {
      Bindable<?> resolvedTarget = this.resolveTarget(target);
      boolean hasDescendants = this.hasDescendants(name);
      if (!hasDescendants && !ConfigurationPropertyName.EMPTY.equals(name)) {
         for (ConfigurationPropertySource source : this.getContext().getSources()) {
            ConfigurationProperty property = source.getConfigurationProperty(name);
            if (property != null) {
               this.getContext().setConfigurationProperty(property);
               Object result = this.getContext().getPlaceholdersResolver().resolvePlaceholders(property.getValue());
               if (result instanceof CharSequence charSequence && charSequence.isEmpty()) {
                  return this.createMap(target);
               }

               return this.getContext().getConverter().convert(result, target);
            }
         }
      }

      Map<Object, Object> map = this.createMap(target);

      for (ConfigurationPropertySource source : this.getContext().getSources()) {
         if (!ConfigurationPropertyName.EMPTY.equals(name)) {
            source = source.filter(name::isAncestorOf);
         }

         new MapBinder.EntryBinder(name, resolvedTarget, elementBinder).bindEntries(source, map);
      }

      return map.isEmpty() ? null : map;
   }

   private Map<Object, Object> createMap(Bindable<?> target) {
      Class<?> mapType = target.getValue() != null ? Map.class : target.getType().resolve(Object.class);
      if (EnumMap.class.isAssignableFrom(mapType)) {
         Class<?> keyType = target.getType().asMap().resolveGeneric(new int[]{0});
         return CollectionFactory.createMap(mapType, keyType, 0);
      } else {
         return CollectionFactory.createMap(mapType, 0);
      }
   }

   private boolean hasDescendants(ConfigurationPropertyName name) {
      for (ConfigurationPropertySource source : this.getContext().getSources()) {
         if (source.containsDescendantOf(name) == ConfigurationPropertyState.PRESENT) {
            return true;
         }
      }

      return false;
   }

   private Bindable<?> resolveTarget(Bindable<?> target) {
      Class<?> type = target.getType().resolve(Object.class);
      return Properties.class.isAssignableFrom(type) ? STRING_STRING_MAP : target;
   }

   protected Map<Object, Object> merge(Supplier<Map<Object, Object>> existing, Map<Object, Object> additional) {
      Map<Object, Object> existingMap = this.getExistingIfPossible(existing);
      if (existingMap == null) {
         return additional;
      }

      try {
         existingMap.putAll(additional);
         return this.copyIfPossible(existingMap);
      } catch (UnsupportedOperationException ex) {
         Map<Object, Object> result = this.createNewMap(additional.getClass(), existingMap);
         result.putAll(additional);
         return result;
      }
   }

   private @Nullable Map<Object, Object> getExistingIfPossible(Supplier<Map<Object, Object>> existing) {
      try {
         return existing.get();
      } catch (Exception ex) {
         return null;
      }
   }

   private Map<Object, Object> copyIfPossible(Map<Object, Object> map) {
      try {
         return this.createNewMap(map.getClass(), map);
      } catch (Exception ex) {
         return map;
      }
   }

   private Map<Object, Object> createNewMap(Class<?> mapClass, Map<Object, Object> map) {
      Map<Object, Object> result = CollectionFactory.createMap(mapClass, map.size());
      result.putAll(map);
      return result;
   }

   private class EntryBinder {
      private final ConfigurationPropertyName root;
      private final AggregateElementBinder elementBinder;
      private final ResolvableType mapType;
      private final ResolvableType keyType;
      private final ResolvableType valueType;
      private final Class<?> resolvedValueType;
      private final boolean valueTreatedAsNestedMap;
      private final Bindable<Object> bindableMapType;
      private final Bindable<Object> bindableValueType;

      EntryBinder(ConfigurationPropertyName root, Bindable<?> target, AggregateElementBinder elementBinder) {
         this.root = root;
         this.elementBinder = elementBinder;
         this.mapType = target.getType().asMap();
         this.keyType = this.mapType.getGeneric(new int[]{0});
         this.valueType = this.mapType.getGeneric(new int[]{1});
         this.resolvedValueType = this.valueType.resolve(Object.class);
         this.valueTreatedAsNestedMap = Object.class.equals(this.resolvedValueType);
         this.bindableMapType = Bindable.of(this.mapType);
         this.bindableValueType = Bindable.of(this.valueType);
      }

      void bindEntries(ConfigurationPropertySource source, Map<Object, Object> map) {
         if (source instanceof IterableConfigurationPropertySource) {
            for (ConfigurationPropertyName name : (IterableConfigurationPropertySource)source) {
               ConfigurationPropertyName entryName = this.getEntryName(source, name);
               Object key = MapBinder.this.getContext().getConverter().convert(this.getKeyName(entryName), this.keyType);
               Bindable<?> valueBindable = this.getValueBindable(name);
               map.computeIfAbsent(key, k -> this.elementBinder.bind(entryName, valueBindable));
            }
         }
      }

      private Bindable<?> getValueBindable(ConfigurationPropertyName name) {
         return !this.isParentOf(name) && this.valueTreatedAsNestedMap ? this.bindableMapType : this.bindableValueType;
      }

      private ConfigurationPropertyName getEntryName(ConfigurationPropertySource source, ConfigurationPropertyName name) {
         if (!Collection.class.isAssignableFrom(this.resolvedValueType) && !this.valueType.isArray()) {
            return this.isParentOf(name) || !this.valueTreatedAsNestedMap && this.isScalarValue(source, name)
               ? name
               : name.chop(this.root.getNumberOfElements() + 1);
         } else {
            return this.chopNameAtNumericIndex(name);
         }
      }

      private boolean isParentOf(ConfigurationPropertyName name) {
         return this.root.isParentOf(name);
      }

      private ConfigurationPropertyName chopNameAtNumericIndex(ConfigurationPropertyName name) {
         int start = this.root.getNumberOfElements() + 1;
         int size = name.getNumberOfElements();

         for (int i = start; i < size; i++) {
            if (name.isNumericIndex(i)) {
               return name.chop(i);
            }
         }

         return name;
      }

      private boolean isScalarValue(ConfigurationPropertySource source, ConfigurationPropertyName name) {
         Class<?> resolved = this.valueType.resolve(Object.class);
         if (!resolved.getName().startsWith("java.lang") && !resolved.isEnum()) {
            return false;
         }

         ConfigurationProperty property = source.getConfigurationProperty(name);
         if (property == null) {
            return false;
         }

         Object value = property.getValue();
         value = MapBinder.this.getContext().getPlaceholdersResolver().resolvePlaceholders(value);
         return MapBinder.this.getContext().getConverter().canConvert(value, this.valueType);
      }

      private String getKeyName(ConfigurationPropertyName name) {
         StringBuilder result = new StringBuilder();

         for (int i = this.root.getNumberOfElements(); i < name.getNumberOfElements(); i++) {
            if (!result.isEmpty()) {
               result.append('.');
            }

            result.append(name.getElement(i, ConfigurationPropertyName.Form.ORIGINAL));
         }

         return result.toString();
      }
   }
}
