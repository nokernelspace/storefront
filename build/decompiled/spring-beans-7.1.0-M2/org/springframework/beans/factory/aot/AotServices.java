package org.springframework.beans.factory.aot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

public final class AotServices<T> implements Iterable<T> {
   public static final String FACTORIES_RESOURCE_LOCATION = "META-INF/spring/aot.factories";
   private final List<T> services;
   private final Map<String, T> beans;
   private final Map<T, AotServices.Source> sources;

   private AotServices(List<T> loaded, Map<String, T> beans) {
      this.services = this.collectServices(loaded, beans);
      this.sources = this.collectSources(loaded, beans.values());
      this.beans = beans;
   }

   private List<T> collectServices(List<T> loaded, Map<String, T> beans) {
      List<T> services = new ArrayList<>();
      services.addAll(beans.values());
      services.addAll(loaded);
      AnnotationAwareOrderComparator.sort(services);
      return Collections.unmodifiableList(services);
   }

   private Map<T, AotServices.Source> collectSources(Collection<T> loaded, Collection<T> beans) {
      Map<T, AotServices.Source> sources = new IdentityHashMap<>();
      loaded.forEach(service -> sources.put((T)service, AotServices.Source.SPRING_FACTORIES_LOADER));
      beans.forEach(service -> sources.put((T)service, AotServices.Source.BEAN_FACTORY));
      return Collections.unmodifiableMap(sources);
   }

   public static AotServices.Loader factories() {
      return factories((ClassLoader)null);
   }

   public static AotServices.Loader factories(@Nullable ClassLoader classLoader) {
      return factories(getSpringFactoriesLoader(classLoader));
   }

   public static AotServices.Loader factories(SpringFactoriesLoader springFactoriesLoader) {
      Assert.notNull(springFactoriesLoader, "'springFactoriesLoader' must not be null");
      return new AotServices.Loader(springFactoriesLoader, null);
   }

   public static AotServices.Loader factoriesAndBeans(ListableBeanFactory beanFactory) {
      ClassLoader classLoader = beanFactory instanceof ConfigurableBeanFactory configurableBeanFactory ? configurableBeanFactory.getBeanClassLoader() : null;
      return factoriesAndBeans(getSpringFactoriesLoader(classLoader), beanFactory);
   }

   public static AotServices.Loader factoriesAndBeans(SpringFactoriesLoader springFactoriesLoader, ListableBeanFactory beanFactory) {
      Assert.notNull(beanFactory, "'beanFactory' must not be null");
      Assert.notNull(springFactoriesLoader, "'springFactoriesLoader' must not be null");
      return new AotServices.Loader(springFactoriesLoader, beanFactory);
   }

   private static SpringFactoriesLoader getSpringFactoriesLoader(@Nullable ClassLoader classLoader) {
      return SpringFactoriesLoader.forResourceLocation("META-INF/spring/aot.factories", classLoader);
   }

   @Override
   public Iterator<T> iterator() {
      return this.services.iterator();
   }

   public Stream<T> stream() {
      return this.services.stream();
   }

   public List<T> asList() {
      return this.services;
   }

   public @Nullable T findByBeanName(String beanName) {
      return this.beans.get(beanName);
   }

   public AotServices.Source getSource(T service) {
      AotServices.Source source = this.sources.get(service);
      Assert.state(source != null, () -> "Unable to find service " + ObjectUtils.identityToString(source));
      return source;
   }

   public static class Loader {
      private final SpringFactoriesLoader springFactoriesLoader;
      private final @Nullable ListableBeanFactory beanFactory;

      Loader(SpringFactoriesLoader springFactoriesLoader, @Nullable ListableBeanFactory beanFactory) {
         this.springFactoriesLoader = springFactoriesLoader;
         this.beanFactory = beanFactory;
      }

      public <T> AotServices<T> load(Class<T> type) {
         return new AotServices<>(this.springFactoriesLoader.load(type), this.loadBeans(type));
      }

      private <T> Map<String, T> loadBeans(Class<T> type) {
         return this.beanFactory != null ? BeanFactoryUtils.beansOfTypeIncludingAncestors(this.beanFactory, type, true, false) : Collections.emptyMap();
      }
   }

   public enum Source {
      SPRING_FACTORIES_LOADER,
      BEAN_FACTORY;
   }
}
