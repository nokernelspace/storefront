package org.springframework.core.env;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public class CompositePropertySource extends EnumerablePropertySource<Object> {
   private final Set<PropertySource<?>> propertySources = new LinkedHashSet<>();

   public CompositePropertySource(String name) {
      super(name);
   }

   @Override
   public @Nullable Object getProperty(String name) {
      for (PropertySource<?> propertySource : this.propertySources) {
         Object candidate = propertySource.getProperty(name);
         if (candidate != null) {
            return candidate;
         }
      }

      return null;
   }

   @Override
   public boolean containsProperty(String name) {
      for (PropertySource<?> propertySource : this.propertySources) {
         if (propertySource.containsProperty(name)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public String[] getPropertyNames() {
      List<String[]> namesList = new ArrayList<>(this.propertySources.size());
      int total = 0;

      for (PropertySource<?> propertySource : this.propertySources) {
         if (!(propertySource instanceof EnumerablePropertySource<?> enumerablePropertySource)) {
            throw new IllegalStateException("Failed to enumerate property names due to non-enumerable property source: " + propertySource);
         }

         String[] names = enumerablePropertySource.getPropertyNames();
         namesList.add(names);
         total += names.length;
      }

      Set<String> allNames = CollectionUtils.newLinkedHashSet(total);
      namesList.forEach(namesx -> Collections.addAll(allNames, namesx));
      return StringUtils.toStringArray(allNames);
   }

   public void addPropertySource(PropertySource<?> propertySource) {
      this.propertySources.add(propertySource);
   }

   public void addFirstPropertySource(PropertySource<?> propertySource) {
      List<PropertySource<?>> existing = new ArrayList<>(this.propertySources);
      this.propertySources.clear();
      this.propertySources.add(propertySource);
      this.propertySources.addAll(existing);
   }

   public Collection<PropertySource<?>> getPropertySources() {
      return this.propertySources;
   }

   @Override
   public String toString() {
      return this.getClass().getSimpleName() + " {name='" + this.name + "', propertySources=" + this.propertySources + "}";
   }
}
