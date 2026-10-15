package org.springframework.boot.context.annotation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.core.OrderComparator;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

public abstract class Configurations {
   private static final Comparator<Object> COMPARATOR = OrderComparator.INSTANCE.thenComparing(other -> other.getClass().getName());
   private final @Nullable UnaryOperator<Collection<Class<?>>> sorter;
   private final Set<Class<?>> classes;
   private final @Nullable Function<Class<?>, String> beanNameGenerator;

   protected Configurations(Collection<Class<?>> classes) {
      Assert.notNull(classes, "'classes' must not be null");
      this.sorter = null;
      this.classes = Collections.unmodifiableSet(new LinkedHashSet<>(classes));
      this.beanNameGenerator = null;
   }

   protected Configurations(
      @Nullable UnaryOperator<Collection<Class<?>>> sorter, Collection<Class<?>> classes, @Nullable Function<Class<?>, String> beanNameGenerator
   ) {
      Assert.notNull(classes, "'classes' must not be null");
      this.sorter = sorter != null ? sorter : UnaryOperator.identity();
      Collection<Class<?>> sorted = this.sorter.apply(classes);
      this.classes = Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
      this.beanNameGenerator = beanNameGenerator;
   }

   protected final Set<Class<?>> getClasses() {
      return this.classes;
   }

   protected Configurations merge(Configurations other) {
      Set<Class<?>> mergedClasses = new LinkedHashSet<>(this.getClasses());
      mergedClasses.addAll(other.getClasses());
      if (this.sorter != null) {
         mergedClasses = new LinkedHashSet<>(this.sorter.apply(mergedClasses));
      }

      return this.merge(mergedClasses);
   }

   protected abstract Configurations merge(Set<Class<?>> mergedClasses);

   public @Nullable String getBeanName(Class<?> beanClass) {
      return this.beanNameGenerator != null ? this.beanNameGenerator.apply(beanClass) : null;
   }

   public static Class<?>[] getClasses(Configurations... configurations) {
      return getClasses(Arrays.asList(configurations));
   }

   public static Class<?>[] getClasses(Collection<Configurations> configurations) {
      List<Configurations> collated = collate(configurations);
      LinkedHashSet<Class<?>> classes = collated.stream().flatMap(Configurations::streamClasses).collect(Collectors.toCollection(LinkedHashSet::new));
      return ClassUtils.toClassArray(classes);
   }

   public static List<Configurations> collate(Collection<Configurations> configurations) {
      LinkedList<Configurations> collated = new LinkedList<>();

      for (Configurations configuration : sortConfigurations(configurations)) {
         if (!collated.isEmpty() && collated.getLast().getClass() == configuration.getClass()) {
            collated.set(collated.size() - 1, collated.getLast().merge(configuration));
         } else {
            collated.add(configuration);
         }
      }

      return collated;
   }

   private static List<Configurations> sortConfigurations(Collection<Configurations> configurations) {
      List<Configurations> sorted = new ArrayList<>(configurations);
      sorted.sort(COMPARATOR);
      return sorted;
   }

   private static Stream<Class<?>> streamClasses(Configurations configurations) {
      return configurations.getClasses().stream();
   }
}
