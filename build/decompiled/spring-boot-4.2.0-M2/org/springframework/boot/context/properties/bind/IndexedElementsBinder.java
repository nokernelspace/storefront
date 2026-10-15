package org.springframework.boot.context.properties.bind;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.source.ConfigurationProperty;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.core.ResolvableType;

abstract class IndexedElementsBinder<T> extends AggregateBinder<T> {
   private static final String[] INDEXES = new String[10];

   IndexedElementsBinder(Binder.Context context) {
      super(context);
   }

   @Override
   protected boolean isAllowRecursiveBinding(@Nullable ConfigurationPropertySource source) {
      return source == null || source instanceof IterableConfigurationPropertySource;
   }

   protected final void bindIndexed(
      ConfigurationPropertyName name,
      Bindable<?> target,
      AggregateElementBinder elementBinder,
      ResolvableType aggregateType,
      ResolvableType elementType,
      IndexedElementsBinder.IndexedCollectionSupplier result
   ) {
      for (ConfigurationPropertySource source : this.getContext().getSources()) {
         this.bindIndexed(source, name, target, elementBinder, result, aggregateType, elementType);
         if (result.wasSupplied() && result.get() != null) {
            return;
         }
      }
   }

   private void bindIndexed(
      ConfigurationPropertySource source,
      ConfigurationPropertyName root,
      Bindable<?> target,
      AggregateElementBinder elementBinder,
      IndexedElementsBinder.IndexedCollectionSupplier collection,
      ResolvableType aggregateType,
      ResolvableType elementType
   ) {
      ConfigurationProperty property = source.getConfigurationProperty(root);
      if (property != null) {
         this.getContext().setConfigurationProperty(property);
         this.bindValue(target, (Collection<Object>)collection.get(), aggregateType, elementType, property.getValue());
      } else {
         this.bindIndexed(source, root, elementBinder, collection, elementType);
      }
   }

   private void bindValue(Bindable<?> target, Collection<Object> collection, ResolvableType aggregateType, ResolvableType elementType, @Nullable Object value) {
      if (value != null && !(value instanceof CharSequence charSequence && charSequence.isEmpty())) {
         Object aggregate = this.convert(value, aggregateType, target.getAnnotations());
         ResolvableType collectionType = ResolvableType.forClassWithGenerics(collection.getClass(), new ResolvableType[]{elementType});
         Collection<Object> elements = this.convert(aggregate, collectionType);
         if (elements != null) {
            collection.addAll(elements);
         }
      }
   }

   private void bindIndexed(
      ConfigurationPropertySource source,
      ConfigurationPropertyName root,
      AggregateElementBinder elementBinder,
      IndexedElementsBinder.IndexedCollectionSupplier collection,
      ResolvableType elementType
   ) {
      Set<String> knownIndexedChildren = new HashSet<>();
      if (source instanceof IterableConfigurationPropertySource iterableSource) {
         knownIndexedChildren = this.getKnownIndexedChildren(iterableSource, root);
      }

      for (int i = 0; i < Integer.MAX_VALUE; i++) {
         ConfigurationPropertyName name = this.appendIndex(root, i);
         Object value = elementBinder.bind(name, Bindable.of(elementType), source);
         if (value == null) {
            break;
         }

         knownIndexedChildren.remove(name.getLastElement(ConfigurationPropertyName.Form.UNIFORM));
         ((Collection)collection.get()).add(value);
      }

      if (source instanceof IterableConfigurationPropertySource iterableSource) {
         this.assertNoUnboundChildren(knownIndexedChildren, iterableSource, root);
      }
   }

   private Set<String> getKnownIndexedChildren(IterableConfigurationPropertySource source, ConfigurationPropertyName root) {
      Set<String> knownIndexedChildren = new HashSet<>();

      for (ConfigurationPropertyName name : source.filter(root::isAncestorOf)) {
         ConfigurationPropertyName choppedName = name.chop(root.getNumberOfElements() + 1);
         if (choppedName.isLastElementIndexed()) {
            knownIndexedChildren.add(choppedName.getLastElement(ConfigurationPropertyName.Form.UNIFORM));
         }
      }

      return knownIndexedChildren;
   }

   private void assertNoUnboundChildren(Set<String> unboundIndexedChildren, IterableConfigurationPropertySource source, ConfigurationPropertyName root) {
      if (!unboundIndexedChildren.isEmpty()) {
         Set<ConfigurationProperty> unboundProperties = new TreeSet<>();

         for (ConfigurationPropertyName name : source.filter(root::isAncestorOf)) {
            ConfigurationPropertyName choppedName = name.chop(root.getNumberOfElements() + 1);
            if (choppedName.isLastElementIndexed() && unboundIndexedChildren.contains(choppedName.getLastElement(ConfigurationPropertyName.Form.UNIFORM))) {
               unboundProperties.add(source.getConfigurationProperty(name));
            }
         }

         if (!unboundProperties.isEmpty()) {
            throw new UnboundConfigurationPropertiesException(unboundProperties);
         }
      }
   }

   private ConfigurationPropertyName appendIndex(ConfigurationPropertyName root, int i) {
      return root.append(i < INDEXES.length ? INDEXES[i] : "[" + i + "]");
   }

   private <C> @Nullable C convert(@Nullable Object value, ResolvableType type, Annotation... annotations) {
      value = this.getContext().getPlaceholdersResolver().resolvePlaceholders(value);
      return this.getContext().getConverter().convert(value, type, annotations);
   }

   static {
      for (int i = 0; i < INDEXES.length; i++) {
         INDEXES[i] = "[" + i + "]";
      }
   }

   protected static class IndexedCollectionSupplier extends AggregateBinder.AggregateSupplier<Collection<Object>> {
      public IndexedCollectionSupplier(Supplier<Collection<Object>> supplier) {
         super(supplier);
      }
   }
}
