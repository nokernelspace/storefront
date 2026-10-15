package org.springframework.boot.context.properties.source;

import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.env.PropertySourceInfo;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.PropertySourceOrigin;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;

class SpringIterableConfigurationPropertySource
   extends SpringConfigurationPropertySource
   implements IterableConfigurationPropertySource,
   CachingConfigurationPropertySource {
   private final BiPredicate<ConfigurationPropertyName, ConfigurationPropertyName> ancestorOfCheck;
   private final SoftReferenceConfigurationPropertyCache<SpringIterableConfigurationPropertySource.Cache> cache;
   private volatile @Nullable ConfigurationPropertyName @Nullable [] configurationPropertyNames;
   private final @Nullable Map<ConfigurationPropertyName, ConfigurationPropertyState> containsDescendantOfCache;

   SpringIterableConfigurationPropertySource(EnumerablePropertySource<?> propertySource, boolean systemEnvironmentSource, PropertyMapper... mappers) {
      super(propertySource, systemEnvironmentSource, mappers);
      this.assertEnumerablePropertySource();
      boolean immutable = this.isImmutablePropertySource();
      this.ancestorOfCheck = this.getAncestorOfCheck(mappers);
      this.cache = new SoftReferenceConfigurationPropertyCache<>(immutable);
      this.containsDescendantOfCache = !systemEnvironmentSource ? null : new ConcurrentReferenceHashMap();
   }

   private BiPredicate<ConfigurationPropertyName, ConfigurationPropertyName> getAncestorOfCheck(PropertyMapper[] mappers) {
      BiPredicate<ConfigurationPropertyName, ConfigurationPropertyName> ancestorOfCheck = mappers[0].getAncestorOfCheck();

      for (int i = 1; i < mappers.length; i++) {
         ancestorOfCheck = ancestorOfCheck.or(mappers[i].getAncestorOfCheck());
      }

      return ancestorOfCheck;
   }

   private void assertEnumerablePropertySource() {
      if (this.getPropertySource() instanceof MapPropertySource mapSource) {
         try {
            ((Map)mapSource.getSource()).size();
         } catch (UnsupportedOperationException ex) {
            throw new IllegalArgumentException("PropertySource must be fully enumerable");
         }
      }
   }

   @Override
   public ConfigurationPropertyCaching getCaching() {
      return this.cache;
   }

   @Override
   public @Nullable ConfigurationProperty getConfigurationProperty(@Nullable ConfigurationPropertyName name) {
      if (name == null) {
         return null;
      }

      ConfigurationProperty configurationProperty = super.getConfigurationProperty(name);
      if (configurationProperty != null) {
         return configurationProperty;
      }

      for (String candidate : this.getCache().getMapped(name)) {
         Object value = this.getPropertySourceProperty(candidate);
         if (value != null) {
            Origin origin = PropertySourceOrigin.get(this.getPropertySource(), candidate);
            return ConfigurationProperty.of(this, name, value, origin);
         }
      }

      return null;
   }

   @Override
   protected @Nullable Object getSystemEnvironmentProperty(Map<String, Object> systemEnvironment, String name) {
      return this.getCache().getSystemEnvironmentProperty(name);
   }

   @Override
   public Stream<ConfigurationPropertyName> stream() {
      ConfigurationPropertyName[] names = this.getConfigurationPropertyNames();
      return Arrays.stream(names).filter(Objects::nonNull);
   }

   @Override
   public Iterator<ConfigurationPropertyName> iterator() {
      return new SpringIterableConfigurationPropertySource.ConfigurationPropertyNamesIterator(this.getConfigurationPropertyNames());
   }

   @Override
   public ConfigurationPropertyState containsDescendantOf(ConfigurationPropertyName name) {
      ConfigurationPropertyState result = super.containsDescendantOf(name);
      if (result != ConfigurationPropertyState.UNKNOWN) {
         return result;
      }

      if (this.ancestorOfCheck == PropertyMapper.DEFAULT_ANCESTOR_OF_CHECK) {
         Set<ConfigurationPropertyName> descendants = this.getCache().getDescendants();
         if (descendants != null) {
            if (name.isEmpty() && !descendants.isEmpty()) {
               return ConfigurationPropertyState.PRESENT;
            }

            return !descendants.contains(name) ? ConfigurationPropertyState.ABSENT : ConfigurationPropertyState.PRESENT;
         }
      }

      result = this.containsDescendantOfCache != null ? this.containsDescendantOfCache.get(name) : null;
      if (result == null) {
         result = !this.ancestorOfCheck(name) ? ConfigurationPropertyState.ABSENT : ConfigurationPropertyState.PRESENT;
         if (this.containsDescendantOfCache != null) {
            this.containsDescendantOfCache.put(name, result);
         }
      }

      return result;
   }

   private boolean ancestorOfCheck(ConfigurationPropertyName name) {
      ConfigurationPropertyName[] candidates = this.getConfigurationPropertyNames();

      for (ConfigurationPropertyName candidate : candidates) {
         if (candidate != null && this.ancestorOfCheck.test(name, candidate)) {
            return true;
         }
      }

      return false;
   }

   ConfigurationPropertyName[] getConfigurationPropertyNames() {
      if (!this.isImmutablePropertySource()) {
         return this.getCache().getConfigurationPropertyNames(this.getPropertySource().getPropertyNames());
      }

      ConfigurationPropertyName[] configurationPropertyNames = this.configurationPropertyNames;
      if (configurationPropertyNames == null) {
         configurationPropertyNames = this.getCache().getConfigurationPropertyNames(this.getPropertySource().getPropertyNames());
         this.configurationPropertyNames = configurationPropertyNames;
      }

      return configurationPropertyNames;
   }

   private SpringIterableConfigurationPropertySource.Cache getCache() {
      return this.cache.get(this::createCache, this::updateCache);
   }

   private SpringIterableConfigurationPropertySource.Cache createCache() {
      boolean immutable = this.isImmutablePropertySource();
      boolean captureDescendants = this.ancestorOfCheck == PropertyMapper.DEFAULT_ANCESTOR_OF_CHECK;
      return new SpringIterableConfigurationPropertySource.Cache(this.getMappers(), immutable, captureDescendants, this.isSystemEnvironmentSource());
   }

   private SpringIterableConfigurationPropertySource.Cache updateCache(SpringIterableConfigurationPropertySource.Cache cache) {
      cache.update(this.getPropertySource());
      return cache;
   }

   boolean isImmutablePropertySource() {
      EnumerablePropertySource<?> source = this.getPropertySource();
      if (source instanceof PropertySourceInfo propertySourceInfo) {
         return propertySourceInfo.isImmutable();
      } else {
         return "systemEnvironment".equals(source.getName()) ? source.getSource() == System.getenv() : false;
      }
   }

   protected EnumerablePropertySource<?> getPropertySource() {
      return (EnumerablePropertySource<?>)super.getPropertySource();
   }

   private static class Cache {
      private static final ConfigurationPropertyName[] EMPTY_NAMES_ARRAY = new ConfigurationPropertyName[0];
      private final PropertyMapper[] mappers;
      private final boolean immutable;
      private final boolean captureDescendants;
      private final boolean systemEnvironmentSource;
      private volatile SpringIterableConfigurationPropertySource.Cache.@Nullable Data data;

      Cache(PropertyMapper[] mappers, boolean immutable, boolean captureDescendants, boolean systemEnvironmentSource) {
         this.mappers = mappers;
         this.immutable = immutable;
         this.captureDescendants = captureDescendants;
         this.systemEnvironmentSource = systemEnvironmentSource;
      }

      void update(EnumerablePropertySource<?> propertySource) {
         if (this.data == null || !this.immutable) {
            int count = 0;

            while (true) {
               try {
                  this.tryUpdate(propertySource);
                  return;
               } catch (ConcurrentModificationException ex) {
                  if (count++ > 10) {
                     throw ex;
                  }
               }
            }
         }
      }

      private void tryUpdate(EnumerablePropertySource<?> propertySource) {
         SpringIterableConfigurationPropertySource.Cache.Data data = this.data;
         String[] lastUpdated = data != null ? data.lastUpdated() : null;
         String[] propertyNames = propertySource.getPropertyNames();
         if (lastUpdated == null || !Arrays.equals(lastUpdated, propertyNames)) {
            int size = propertyNames.length;
            Map<ConfigurationPropertyName, Set<String>> mappings = this.cloneOrCreate(data != null ? data.mappings() : null, size);
            Map<String, ConfigurationPropertyName> reverseMappings = this.cloneOrCreate(data != null ? data.reverseMappings() : null, size);
            Set<ConfigurationPropertyName> descendants = !this.captureDescendants ? null : new HashSet<>();
            Map<String, Object> systemEnvironmentCopy = !this.systemEnvironmentSource ? null : this.copySource(propertySource);

            for (PropertyMapper propertyMapper : this.mappers) {
               for (String propertyName : propertyNames) {
                  if (!reverseMappings.containsKey(propertyName)) {
                     ConfigurationPropertyName configurationPropertyName = propertyMapper.map(propertyName);
                     if (configurationPropertyName != null && !configurationPropertyName.isEmpty()) {
                        this.add(mappings, configurationPropertyName, propertyName);
                        reverseMappings.put(propertyName, configurationPropertyName);
                     }
                  }
               }
            }

            for (String propertyName : propertyNames) {
               this.addParents(descendants, reverseMappings.get(propertyName));
            }

            ConfigurationPropertyName[] configurationPropertyNames = this.immutable ? reverseMappings.values().toArray(new ConfigurationPropertyName[0]) : null;
            lastUpdated = this.immutable ? null : propertyNames;
            this.data = new SpringIterableConfigurationPropertySource.Cache.Data(
               mappings, reverseMappings, descendants, configurationPropertyNames, systemEnvironmentCopy, lastUpdated
            );
         }
      }

      private HashMap<String, Object> copySource(EnumerablePropertySource<?> propertySource) {
         return new HashMap<>((Map<? extends String, ? extends Object>)propertySource.getSource());
      }

      private <K, V> Map<K, V> cloneOrCreate(@Nullable Map<K, V> source, int size) {
         return source != null ? new LinkedHashMap<>(source) : new LinkedHashMap<>(size);
      }

      private void addParents(@Nullable Set<ConfigurationPropertyName> descendants, @Nullable ConfigurationPropertyName name) {
         if (descendants != null && name != null && !name.isEmpty()) {
            for (ConfigurationPropertyName parent = name.getParent(); !parent.isEmpty(); parent = parent.getParent()) {
               if (!descendants.add(parent)) {
                  return;
               }
            }
         }
      }

      private <K, T> void add(Map<K, Set<T>> map, K key, T value) {
         map.computeIfAbsent(key, k -> new HashSet<>()).add(value);
      }

      Set<String> getMapped(ConfigurationPropertyName configurationPropertyName) {
         SpringIterableConfigurationPropertySource.Cache.Data data = this.data;
         Assert.state(data != null, "'data' must not be null");
         return data.mappings().getOrDefault(configurationPropertyName, Collections.emptySet());
      }

      ConfigurationPropertyName[] getConfigurationPropertyNames(String[] propertyNames) {
         SpringIterableConfigurationPropertySource.Cache.Data data = this.data;
         Assert.state(data != null, "'data' must not be null");
         ConfigurationPropertyName[] names = data.configurationPropertyNames();
         if (names != null) {
            return names;
         }

         Map<String, ConfigurationPropertyName> reverseMappings = data.reverseMappings();
         if (reverseMappings != null && !reverseMappings.isEmpty()) {
            names = new ConfigurationPropertyName[propertyNames.length];

            for (int i = 0; i < propertyNames.length; i++) {
               names[i] = reverseMappings.get(propertyNames[i]);
            }

            return names;
         } else {
            return EMPTY_NAMES_ARRAY;
         }
      }

      @Nullable Set<ConfigurationPropertyName> getDescendants() {
         SpringIterableConfigurationPropertySource.Cache.Data data = this.data;
         Assert.state(data != null, "'data' must not be null");
         return data.descendants();
      }

      @Nullable Object getSystemEnvironmentProperty(String name) {
         SpringIterableConfigurationPropertySource.Cache.Data data = this.data;
         Assert.state(data != null, "'data' must not be null");
         Map<String, Object> systemEnvironmentCopy = data.systemEnvironmentCopy();
         Assert.state(systemEnvironmentCopy != null, "'systemEnvironmentCopy' must not be null");
         return systemEnvironmentCopy.get(name);
      }

      private record Data(
         Map<ConfigurationPropertyName, Set<String>> mappings,
         Map<String, ConfigurationPropertyName> reverseMappings,
         @Nullable Set<ConfigurationPropertyName> descendants,
         ConfigurationPropertyName @Nullable [] configurationPropertyNames,
         @Nullable Map<String, Object> systemEnvironmentCopy,
         String @Nullable [] lastUpdated
      ) {
      }
   }

   private static class ConfigurationPropertyNamesIterator implements Iterator<ConfigurationPropertyName> {
      private final @Nullable ConfigurationPropertyName[] names;
      private int index;

      ConfigurationPropertyNamesIterator(@Nullable ConfigurationPropertyName[] names) {
         this.names = names;
      }

      @Override
      public boolean hasNext() {
         this.skipNulls();
         return this.index < this.names.length;
      }

      public @Nullable ConfigurationPropertyName next() {
         this.skipNulls();
         if (this.index >= this.names.length) {
            throw new NoSuchElementException();
         } else {
            return this.names[this.index++];
         }
      }

      private void skipNulls() {
         while (this.index < this.names.length) {
            if (this.names[this.index] != null) {
               return;
            }

            this.index++;
         }
      }
   }
}
