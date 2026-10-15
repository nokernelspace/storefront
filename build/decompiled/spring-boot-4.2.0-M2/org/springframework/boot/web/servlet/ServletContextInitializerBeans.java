package org.springframework.boot.web.servlet;

import jakarta.servlet.Filter;
import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.Servlet;
import jakarta.servlet.annotation.WebInitParam;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.Order;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

public class ServletContextInitializerBeans extends AbstractCollection<ServletContextInitializer> {
   private static final String DISPATCHER_SERVLET_NAME = "dispatcherServlet";
   private static final Log logger = LogFactory.getLog(ServletContextInitializerBeans.class);
   private final ServletContextInitializerBeans.Seen seen = new ServletContextInitializerBeans.Seen();
   private final MultiValueMap<Class<?>, ServletContextInitializer> initializers = new LinkedMultiValueMap();
   private final List<Class<? extends ServletContextInitializer>> initializerTypes;
   private final List<ServletContextInitializer> sortedList;

   @SafeVarargs
   public ServletContextInitializerBeans(ListableBeanFactory beanFactory, Class<? extends ServletContextInitializer>... initializerTypes) {
      this.initializerTypes = initializerTypes.length != 0 ? Arrays.asList(initializerTypes) : Collections.singletonList(ServletContextInitializer.class);
      this.addServletContextInitializerBeans(beanFactory);
      this.addAdaptableBeans(beanFactory);
      this.sortedList = this.initializers.values().stream().flatMap(value -> value.stream().sorted(AnnotationAwareOrderComparator.INSTANCE)).toList();
      this.logMappings(this.initializers);
   }

   private void addServletContextInitializerBeans(ListableBeanFactory beanFactory) {
      for (Class<? extends ServletContextInitializer> initializerType : this.initializerTypes) {
         for (Entry<String, ? extends ServletContextInitializer> initializerBean : this.getOrderedBeansOfType(beanFactory, initializerType)) {
            this.addServletContextInitializerBean(initializerBean.getKey(), initializerBean.getValue(), beanFactory);
         }
      }
   }

   private void addServletContextInitializerBean(String beanName, ServletContextInitializer initializer, ListableBeanFactory beanFactory) {
      if (initializer instanceof ServletRegistrationBean<?> servletRegistrationBean) {
         Servlet source = servletRegistrationBean.getServlet();
         this.addServletContextInitializerBean(Servlet.class, beanName, servletRegistrationBean, beanFactory, source);
      } else if (initializer instanceof FilterRegistrationBean<?> filterRegistrationBean) {
         Filter source = filterRegistrationBean.getFilter();
         this.addServletContextInitializerBean(Filter.class, beanName, filterRegistrationBean, beanFactory, source);
      } else if (initializer instanceof DelegatingFilterProxyRegistrationBean registrationBean) {
         String source = registrationBean.getTargetBeanName();
         this.addServletContextInitializerBean(Filter.class, beanName, registrationBean, beanFactory, source);
      } else if (initializer instanceof ServletListenerRegistrationBean<?> registrationBean) {
         EventListener source = registrationBean.getListener();
         this.addServletContextInitializerBean(EventListener.class, beanName, registrationBean, beanFactory, source);
      } else {
         this.addServletContextInitializerBean(ServletContextInitializer.class, beanName, initializer, beanFactory, initializer);
      }
   }

   private void addServletContextInitializerBean(
      Class<?> type, String beanName, ServletContextInitializer initializer, ListableBeanFactory beanFactory, @Nullable Object source
   ) {
      this.initializers.add(type, initializer);
      if (source != null) {
         this.seen.add(type, source);
      }

      if (logger.isTraceEnabled()) {
         String resourceDescription = this.getResourceDescription(beanName, beanFactory);
         int order = this.getOrder(initializer);
         logger.trace("Added existing " + type.getSimpleName() + " initializer bean '" + beanName + "'; order=" + order + ", resource=" + resourceDescription);
      }
   }

   private @Nullable String getResourceDescription(String beanName, ListableBeanFactory beanFactory) {
      return beanFactory instanceof BeanDefinitionRegistry registry ? registry.getBeanDefinition(beanName).getResourceDescription() : "unknown";
   }

   protected void addAdaptableBeans(ListableBeanFactory beanFactory) {
      MultipartConfigElement multipartConfig = this.getMultipartConfig(beanFactory);
      this.addAsRegistrationBean(beanFactory, Servlet.class, new ServletContextInitializerBeans.ServletRegistrationBeanAdapter(multipartConfig, beanFactory));
      this.addAsRegistrationBean(beanFactory, Filter.class, new ServletContextInitializerBeans.FilterRegistrationBeanAdapter(beanFactory));

      for (Class<?> listenerType : ServletListenerRegistrationBean.getSupportedTypes()) {
         this.addAsRegistrationBean(beanFactory, EventListener.class, listenerType, new ServletContextInitializerBeans.ServletListenerRegistrationBeanAdapter());
      }
   }

   private @Nullable MultipartConfigElement getMultipartConfig(ListableBeanFactory beanFactory) {
      List<Entry<String, MultipartConfigElement>> beans = this.getOrderedBeansOfType(beanFactory, MultipartConfigElement.class);
      return beans.isEmpty() ? null : beans.get(0).getValue();
   }

   protected <T> void addAsRegistrationBean(ListableBeanFactory beanFactory, Class<T> type, ServletContextInitializerBeans.RegistrationBeanAdapter<T> adapter) {
      this.addAsRegistrationBean(beanFactory, type, type, adapter);
   }

   private <T, B extends T> void addAsRegistrationBean(
      ListableBeanFactory beanFactory, Class<T> type, Class<B> beanType, ServletContextInitializerBeans.RegistrationBeanAdapter<T> adapter
   ) {
      List<Entry<String, B>> entries = this.getOrderedBeansOfType(beanFactory, beanType, this.seen);

      for (Entry<String, B> entry : entries) {
         String beanName = entry.getKey();
         B bean = entry.getValue();
         if (this.seen.add(type, bean)) {
            RegistrationBean registration = adapter.createRegistrationBean(beanName, (T)bean, entries.size());
            Integer order = this.findOrder(bean);
            if (order != null) {
               registration.setOrder(order);
            }

            this.initializers.add(type, registration);
            if (logger.isTraceEnabled()) {
               logger.trace(
                  "Created "
                     + type.getSimpleName()
                     + " initializer for bean '"
                     + beanName
                     + "'; order="
                     + order
                     + ", resource="
                     + this.getResourceDescription(beanName, beanFactory)
               );
            }
         }
      }
   }

   private int getOrder(Object value) {
      Integer order = this.findOrder(value);
      return order != null ? order : Integer.MAX_VALUE;
   }

   private @Nullable Integer findOrder(Object value) {
      return (new AnnotationAwareOrderComparator() {
         public @Nullable Integer findOrder(Object obj) {
            return super.findOrder(obj);
         }
      }).findOrder(value);
   }

   private <T> List<Entry<String, T>> getOrderedBeansOfType(ListableBeanFactory beanFactory, Class<T> type) {
      return this.getOrderedBeansOfType(beanFactory, type, ServletContextInitializerBeans.Seen.empty());
   }

   private <T> List<Entry<String, T>> getOrderedBeansOfType(ListableBeanFactory beanFactory, Class<T> type, ServletContextInitializerBeans.Seen seen) {
      String[] names = beanFactory.getBeanNamesForType(type, true, false);
      Map<String, T> map = new LinkedHashMap<>();

      for (String name : names) {
         if (!seen.contains(type, name) && !ScopedProxyUtils.isScopedTarget(name)) {
            T bean = (T)beanFactory.getBean(name, type);
            if (!seen.contains(type, bean)) {
               map.put(name, bean);
            }
         }
      }

      List<Entry<String, T>> beans = new ArrayList<>(map.entrySet());
      beans.sort((o1, o2) -> AnnotationAwareOrderComparator.INSTANCE.compare(o1.getValue(), o2.getValue()));
      return beans;
   }

   private void logMappings(MultiValueMap<Class<?>, ServletContextInitializer> initializers) {
      if (logger.isDebugEnabled()) {
         this.logMappings("filters", initializers, Filter.class, FilterRegistrationBean.class);
         this.logMappings("servlets", initializers, Servlet.class, ServletRegistrationBean.class);
      }
   }

   private void logMappings(
      String name, MultiValueMap<Class<?>, ServletContextInitializer> initializers, Class<?> type, Class<? extends RegistrationBean> registrationType
   ) {
      List<ServletContextInitializer> registrations = new ArrayList<>();
      registrations.addAll((Collection<? extends ServletContextInitializer>)initializers.getOrDefault(registrationType, Collections.emptyList()));
      registrations.addAll((Collection<? extends ServletContextInitializer>)initializers.getOrDefault(type, Collections.emptyList()));
      String info = registrations.stream().map(Object::toString).collect(Collectors.joining(", "));
      logger.debug("Mapping " + name + ": " + info);
   }

   @Override
   public Iterator<ServletContextInitializer> iterator() {
      return this.sortedList.iterator();
   }

   @Override
   public int size() {
      return this.sortedList.size();
   }

   private static class FilterRegistrationBeanAdapter implements ServletContextInitializerBeans.RegistrationBeanAdapter<Filter> {
      private final ListableBeanFactory beanFactory;

      FilterRegistrationBeanAdapter(ListableBeanFactory beanFactory) {
         this.beanFactory = beanFactory;
      }

      public RegistrationBean createRegistrationBean(String beanName, Filter source, int totalNumberOfSourceBeans) {
         FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>(source);
         bean.setName(beanName);
         FilterRegistration registrationAnnotation = (FilterRegistration)this.beanFactory.findAnnotationOnBean(beanName, FilterRegistration.class);
         if (registrationAnnotation != null) {
            Order orderAnnotation = (Order)this.beanFactory.findAnnotationOnBean(beanName, Order.class);
            Assert.notNull(orderAnnotation, "'orderAnnotation' must not be null");
            this.configureFromAnnotation(bean, registrationAnnotation, orderAnnotation);
         }

         return bean;
      }

      private void configureFromAnnotation(FilterRegistrationBean<Filter> bean, FilterRegistration registration, Order order) {
         bean.setEnabled(registration.enabled());
         bean.setOrder(order.value());
         if (StringUtils.hasText(registration.name())) {
            bean.setName(registration.name());
         }

         bean.setAsyncSupported(registration.asyncSupported());
         if (registration.dispatcherTypes().length > 0) {
            bean.setDispatcherTypes(EnumSet.copyOf(Arrays.asList(registration.dispatcherTypes())));
         }

         bean.setIgnoreRegistrationFailure(registration.ignoreRegistrationFailure());
         bean.setMatchAfter(registration.matchAfter());
         bean.setServletNames(Arrays.asList(registration.servletNames()));
         bean.setUrlPatterns(Arrays.asList(registration.urlPatterns()));

         for (WebInitParam param : registration.initParameters()) {
            bean.addInitParameter(param.name(), param.value());
         }

         this.beanFactory.getBeanProvider(ServletRegistrationBean.class).forEach(servletRegistrationBean -> {
            for (Class<?> servletClass : registration.servletClasses()) {
               if (servletClass.isInstance(servletRegistrationBean.getServlet())) {
                  bean.addServletRegistrationBeans(servletRegistrationBean);
               }
            }
         });
      }
   }

   @FunctionalInterface
   protected interface RegistrationBeanAdapter<T> {
      RegistrationBean createRegistrationBean(String beanName, T source, int totalNumberOfSourceBeans);
   }

   private static final class Seen {
      private final Map<Class<?>, Set<Object>> seen = new HashMap<>();

      boolean add(Class<?> type, Object object) {
         return this.contains(type, object) ? false : this.seen.computeIfAbsent(type, ignore -> new HashSet<>()).add(object);
      }

      boolean contains(Class<?> type, Object object) {
         if (this.seen.isEmpty()) {
            return false;
         } else {
            return type != ServletContextInitializer.class && this.seen.getOrDefault(type, Collections.emptySet()).contains(object)
               ? true
               : this.seen.getOrDefault(ServletContextInitializer.class, Collections.emptySet()).contains(object);
         }
      }

      static ServletContextInitializerBeans.Seen empty() {
         return new ServletContextInitializerBeans.Seen();
      }
   }

   private static final class ServletListenerRegistrationBeanAdapter implements ServletContextInitializerBeans.RegistrationBeanAdapter<EventListener> {
      public RegistrationBean createRegistrationBean(String beanName, EventListener source, int totalNumberOfSourceBeans) {
         return new ServletListenerRegistrationBean<>(source);
      }
   }

   private static class ServletRegistrationBeanAdapter implements ServletContextInitializerBeans.RegistrationBeanAdapter<Servlet> {
      private final @Nullable MultipartConfigElement multipartConfig;
      private final ListableBeanFactory beanFactory;

      ServletRegistrationBeanAdapter(@Nullable MultipartConfigElement multipartConfig, ListableBeanFactory beanFactory) {
         this.multipartConfig = multipartConfig;
         this.beanFactory = beanFactory;
      }

      public RegistrationBean createRegistrationBean(String beanName, Servlet source, int totalNumberOfSourceBeans) {
         String url = totalNumberOfSourceBeans != 1 ? "/" + beanName + "/" : "/";
         if (beanName.equals("dispatcherServlet")) {
            url = "/";
         }

         ServletRegistrationBean<Servlet> bean = new ServletRegistrationBean<>(source, url);
         bean.setName(beanName);
         bean.setMultipartConfig(this.multipartConfig);
         ServletRegistration registrationAnnotation = (ServletRegistration)this.beanFactory.findAnnotationOnBean(beanName, ServletRegistration.class);
         if (registrationAnnotation != null) {
            Order orderAnnotation = (Order)this.beanFactory.findAnnotationOnBean(beanName, Order.class);
            Assert.notNull(orderAnnotation, "'orderAnnotation' must not be null");
            this.configureFromAnnotation(bean, registrationAnnotation, orderAnnotation);
         }

         return bean;
      }

      private void configureFromAnnotation(ServletRegistrationBean<Servlet> bean, ServletRegistration registration, Order order) {
         bean.setEnabled(registration.enabled());
         bean.setOrder(order.value());
         if (StringUtils.hasText(registration.name())) {
            bean.setName(registration.name());
         }

         bean.setAsyncSupported(registration.asyncSupported());
         bean.setIgnoreRegistrationFailure(registration.ignoreRegistrationFailure());
         bean.setLoadOnStartup(registration.loadOnStartup());
         bean.setUrlMappings(Arrays.asList(registration.urlMappings()));

         for (WebInitParam param : registration.initParameters()) {
            bean.addInitParameter(param.name(), param.value());
         }

         bean.setMultipartConfig(new MultipartConfigElement(registration.multipartConfig()));
      }
   }
}
