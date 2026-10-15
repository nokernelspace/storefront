package org.springframework.beans.factory;

import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.core.OrderComparator;

public interface ObjectProvider<T> extends ObjectFactory<T>, Iterable<T> {
   Predicate<Class<?>> UNFILTERED = clazz -> true;

   @Override
   default T getObject() throws BeansException {
      Iterator<T> it = this.iterator();
      if (!it.hasNext()) {
         throw new NoSuchBeanDefinitionException(Object.class);
      } else {
         T result = it.next();
         if (it.hasNext()) {
            throw new NoUniqueBeanDefinitionException(Object.class, 2, "more than 1 matching bean");
         } else {
            return result;
         }
      }
   }

   default T getObject(@Nullable Object... args) throws BeansException {
      throw new UnsupportedOperationException(
         "Retrieval with arguments not supported -for custom ObjectProvider classes, implement getObject(Object...) for your purposes"
      );
   }

   default @Nullable T getIfAvailable() throws BeansException {
      try {
         return this.getObject();
      } catch (NoUniqueBeanDefinitionException ex) {
         throw ex;
      } catch (NoSuchBeanDefinitionException ex) {
         return null;
      }
   }

   default T getIfAvailable(Supplier<T> defaultSupplier) throws BeansException {
      T dependency = this.getIfAvailable();
      return dependency != null ? dependency : defaultSupplier.get();
   }

   default void ifAvailable(Consumer<T> dependencyConsumer) throws BeansException {
      T dependency = this.getIfAvailable();
      if (dependency != null) {
         dependencyConsumer.accept(dependency);
      }
   }

   default @Nullable T getIfUnique() throws BeansException {
      try {
         return this.getObject();
      } catch (NoSuchBeanDefinitionException ex) {
         return null;
      }
   }

   default T getIfUnique(Supplier<T> defaultSupplier) throws BeansException {
      T dependency = this.getIfUnique();
      return dependency != null ? dependency : defaultSupplier.get();
   }

   default void ifUnique(Consumer<T> dependencyConsumer) throws BeansException {
      T dependency = this.getIfUnique();
      if (dependency != null) {
         dependencyConsumer.accept(dependency);
      }
   }

   @Override
   default Iterator<T> iterator() {
      return this.stream().iterator();
   }

   default Stream<T> stream() {
      throw new UnsupportedOperationException(
         "Element access not supported - for custom ObjectProvider classes, implement stream() to enable all other methods"
      );
   }

   default Stream<T> orderedStream() {
      return this.stream().sorted(OrderComparator.INSTANCE);
   }

   default Stream<T> stream(Predicate<Class<?>> customFilter) {
      return this.stream(customFilter, true);
   }

   default Stream<T> orderedStream(Predicate<Class<?>> customFilter) {
      return this.orderedStream(customFilter, true);
   }

   default Stream<T> stream(Predicate<Class<?>> customFilter, boolean includeNonSingletons) {
      if (!includeNonSingletons) {
         throw new UnsupportedOperationException("Only supports includeNonSingletons=true by default");
      } else {
         return this.stream().filter(obj -> customFilter.test(obj.getClass()));
      }
   }

   default Stream<T> orderedStream(Predicate<Class<?>> customFilter, boolean includeNonSingletons) {
      if (!includeNonSingletons) {
         throw new UnsupportedOperationException("Only supports includeNonSingletons=true by default");
      } else {
         return this.orderedStream().filter(obj -> customFilter.test(obj.getClass()));
      }
   }
}
