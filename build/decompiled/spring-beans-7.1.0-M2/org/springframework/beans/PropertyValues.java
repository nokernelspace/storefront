package org.springframework.beans;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;

public interface PropertyValues extends Iterable<PropertyValue> {
   @Override
   default Iterator<PropertyValue> iterator() {
      return Arrays.asList(this.getPropertyValues()).iterator();
   }

   @Override
   default Spliterator<PropertyValue> spliterator() {
      return Spliterators.spliterator(this.getPropertyValues(), 0);
   }

   default Stream<PropertyValue> stream() {
      return StreamSupport.stream(this.spliterator(), false);
   }

   PropertyValue[] getPropertyValues();

   @Nullable PropertyValue getPropertyValue(String propertyName);

   PropertyValues changesSince(PropertyValues old);

   boolean contains(String propertyName);

   boolean isEmpty();
}
