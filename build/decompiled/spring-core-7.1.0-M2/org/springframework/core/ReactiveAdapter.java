package org.springframework.core;

import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.util.Assert;

public class ReactiveAdapter {
   private final ReactiveTypeDescriptor descriptor;
   private final Function<Object, Publisher<?>> toPublisherFunction;
   private final Function<Publisher<?>, Object> fromPublisherFunction;

   public ReactiveAdapter(
      ReactiveTypeDescriptor descriptor, Function<Object, Publisher<?>> toPublisherFunction, Function<Publisher<?>, Object> fromPublisherFunction
   ) {
      Assert.notNull(descriptor, "'descriptor' is required");
      Assert.notNull(toPublisherFunction, "'toPublisherFunction' is required");
      Assert.notNull(fromPublisherFunction, "'fromPublisherFunction' is required");
      this.descriptor = descriptor;
      this.toPublisherFunction = toPublisherFunction;
      this.fromPublisherFunction = fromPublisherFunction;
   }

   public ReactiveTypeDescriptor getDescriptor() {
      return this.descriptor;
   }

   public Class<?> getReactiveType() {
      return this.getDescriptor().getReactiveType();
   }

   public boolean isMultiValue() {
      return this.getDescriptor().isMultiValue();
   }

   public boolean isNoValue() {
      return this.getDescriptor().isNoValue();
   }

   public boolean supportsEmpty() {
      return this.getDescriptor().supportsEmpty();
   }

   public <T> Publisher<T> toPublisher(@Nullable Object source) {
      if (source == null) {
         source = this.getDescriptor().getEmptyValue();
      }

      return (Publisher<T>)this.toPublisherFunction.apply(source);
   }

   public Object fromPublisher(Publisher<?> publisher) {
      return this.fromPublisherFunction.apply(publisher);
   }
}
