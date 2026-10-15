package org.springframework.boot.context.properties.bind;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.core.ResolvableType;

class ArrayBinder extends IndexedElementsBinder<Object> {
   ArrayBinder(Binder.Context context) {
      super(context);
   }

   @Override
   protected @Nullable Object bindAggregate(ConfigurationPropertyName name, Bindable<?> target, AggregateElementBinder elementBinder) {
      IndexedElementsBinder.IndexedCollectionSupplier result = new IndexedElementsBinder.IndexedCollectionSupplier(ArrayList::new);
      ResolvableType aggregateType = target.getType();
      ResolvableType elementType = target.getType().getComponentType();
      this.bindIndexed(name, target, elementBinder, aggregateType, elementType, result);
      if (!result.wasSupplied()) {
         return null;
      }

      List<Object> list = (List<Object>)result.get();
      Object array = Array.newInstance(elementType.resolve(), list.size());

      for (int i = 0; i < list.size(); i++) {
         Array.set(array, i, list.get(i));
      }

      return array;
   }

   @Override
   protected Object merge(Supplier<Object> existing, Object additional) {
      return additional;
   }
}
