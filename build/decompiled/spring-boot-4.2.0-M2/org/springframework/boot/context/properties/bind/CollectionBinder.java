package org.springframework.boot.context.properties.bind;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.core.CollectionFactory;
import org.springframework.core.ResolvableType;

class CollectionBinder extends IndexedElementsBinder<Collection<Object>> {
   CollectionBinder(Binder.Context context) {
      super(context);
   }

   @Override
   protected @Nullable Object bindAggregate(ConfigurationPropertyName name, Bindable<?> target, AggregateElementBinder elementBinder) {
      ResolvableType aggregateType = ResolvableType.forClassWithGenerics(List.class, target.getType().asCollection().getGenerics());
      ResolvableType elementType = target.getType().asCollection().getGeneric(new int[0]);
      IndexedElementsBinder.IndexedCollectionSupplier result = new IndexedElementsBinder.IndexedCollectionSupplier(
         () -> CollectionFactory.createCollection(List.class, elementType.resolve(), 0)
      );
      this.bindIndexed(name, target, elementBinder, aggregateType, elementType, result);
      return result.wasSupplied() ? result.get() : null;
   }

   protected Collection<Object> merge(Supplier<Collection<Object>> existing, Collection<Object> additional) {
      Collection<Object> existingCollection = this.getExistingIfPossible(existing);
      if (existingCollection == null) {
         return additional;
      }

      try {
         existingCollection.clear();
         existingCollection.addAll(additional);
         return this.copyIfPossible(existingCollection);
      } catch (UnsupportedOperationException ex) {
         return this.createNewCollection(additional);
      }
   }

   private @Nullable Collection<Object> getExistingIfPossible(Supplier<Collection<Object>> existing) {
      try {
         return existing.get();
      } catch (Exception ex) {
         return null;
      }
   }

   private Collection<Object> copyIfPossible(Collection<Object> collection) {
      try {
         return this.createNewCollection(collection);
      } catch (Exception ex) {
         return collection;
      }
   }

   private Collection<Object> createNewCollection(Collection<Object> collection) {
      Collection<Object> result = CollectionFactory.createCollection(collection.getClass(), collection.size());
      result.addAll(collection);
      return result;
   }
}
