package org.springframework.boot.web.servlet;

import jakarta.servlet.Filter;
import jakarta.servlet.ServletContext;
import jakarta.servlet.FilterRegistration.Dynamic;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public abstract class AbstractFilterRegistrationBean<T extends Filter> extends DynamicRegistrationBean<Dynamic> {
   private static final String[] DEFAULT_URL_MAPPINGS = new String[]{"/*"};
   private Set<ServletRegistrationBean<?>> servletRegistrationBeans = new LinkedHashSet<>();
   private Set<String> servletNames = new LinkedHashSet<>();
   private Set<String> urlPatterns = new LinkedHashSet<>();
   private @Nullable EnumSet<DispatcherType> dispatcherTypes;
   private boolean matchAfter;

   AbstractFilterRegistrationBean(ServletRegistrationBean<?>... servletRegistrationBeans) {
      Assert.notNull(servletRegistrationBeans, "'servletRegistrationBeans' must not be null");
      Collections.addAll(this.servletRegistrationBeans, servletRegistrationBeans);
   }

   public void setServletRegistrationBeans(Collection<? extends ServletRegistrationBean<?>> servletRegistrationBeans) {
      Assert.notNull(servletRegistrationBeans, "'servletRegistrationBeans' must not be null");
      this.servletRegistrationBeans = new LinkedHashSet<>(servletRegistrationBeans);
   }

   public Collection<ServletRegistrationBean<?>> getServletRegistrationBeans() {
      return this.servletRegistrationBeans;
   }

   public void addServletRegistrationBeans(ServletRegistrationBean<?>... servletRegistrationBeans) {
      Assert.notNull(servletRegistrationBeans, "'servletRegistrationBeans' must not be null");
      Collections.addAll(this.servletRegistrationBeans, servletRegistrationBeans);
   }

   public void setServletNames(Collection<String> servletNames) {
      Assert.notNull(servletNames, "'servletNames' must not be null");
      this.servletNames = new LinkedHashSet<>(servletNames);
   }

   public Collection<String> getServletNames() {
      return this.servletNames;
   }

   public void addServletNames(String... servletNames) {
      Assert.notNull(servletNames, "'servletNames' must not be null");
      this.servletNames.addAll(Arrays.asList(servletNames));
   }

   public void setUrlPatterns(Collection<String> urlPatterns) {
      Assert.notNull(urlPatterns, "'urlPatterns' must not be null");
      this.urlPatterns = new LinkedHashSet<>(urlPatterns);
   }

   public Collection<String> getUrlPatterns() {
      return this.urlPatterns;
   }

   public void addUrlPatterns(String... urlPatterns) {
      Assert.notNull(urlPatterns, "'urlPatterns' must not be null");
      Collections.addAll(this.urlPatterns, urlPatterns);
   }

   public EnumSet<jakarta.servlet.DispatcherType> determineDispatcherTypes() {
      if (CollectionUtils.isEmpty(this.dispatcherTypes)) {
         T filter = this.getFilter();
         Assert.state(filter != null, "'filter' must not be null");
         return ClassUtils.isPresent("org.springframework.web.filter.OncePerRequestFilter", filter.getClass().getClassLoader())
               && filter instanceof OncePerRequestFilter
            ? EnumSet.allOf(jakarta.servlet.DispatcherType.class)
            : EnumSet.of(jakarta.servlet.DispatcherType.REQUEST);
      } else {
         return EnumSet.copyOf(this.dispatcherTypes);
      }
   }

   public void setDispatcherTypes(jakarta.servlet.DispatcherType first, jakarta.servlet.DispatcherType... rest) {
      this.dispatcherTypes = EnumSet.of(first, rest);
   }

   public void setDispatcherTypes(@Nullable EnumSet<DispatcherType> dispatcherTypes) {
      this.dispatcherTypes = dispatcherTypes;
   }

   public void setMatchAfter(boolean matchAfter) {
      this.matchAfter = matchAfter;
   }

   public boolean isMatchAfter() {
      return this.matchAfter;
   }

   @Override
   protected String getDescription() {
      Filter filter = this.getFilter();
      Assert.notNull(filter, "'filter' must not be null");
      return "filter " + this.getOrDeduceName(filter);
   }

   protected Dynamic addRegistration(String description, ServletContext servletContext) {
      Filter filter = this.getFilter();
      return servletContext.addFilter(this.getOrDeduceName(filter), filter);
   }

   protected void configure(Dynamic registration) {
      super.configure(registration);
      EnumSet<jakarta.servlet.DispatcherType> dispatcherTypes = this.determineDispatcherTypes();
      Set<String> servletNames = new LinkedHashSet<>();

      for (ServletRegistrationBean<?> servletRegistrationBean : this.servletRegistrationBeans) {
         servletNames.add(servletRegistrationBean.getServletName());
      }

      servletNames.addAll(this.servletNames);
      if (servletNames.isEmpty() && this.urlPatterns.isEmpty()) {
         registration.addMappingForUrlPatterns(dispatcherTypes, this.matchAfter, DEFAULT_URL_MAPPINGS);
      } else {
         if (!servletNames.isEmpty()) {
            registration.addMappingForServletNames(dispatcherTypes, this.matchAfter, StringUtils.toStringArray(servletNames));
         }

         if (!this.urlPatterns.isEmpty()) {
            registration.addMappingForUrlPatterns(dispatcherTypes, this.matchAfter, StringUtils.toStringArray(this.urlPatterns));
         }
      }
   }

   public abstract @Nullable T getFilter();

   public String getFilterName() {
      return this.getOrDeduceName(this.getFilter());
   }

   @Override
   public String toString() {
      StringBuilder builder = new StringBuilder(this.getOrDeduceName(this));
      if (this.servletNames.isEmpty() && this.urlPatterns.isEmpty()) {
         builder.append(" urls=").append(Arrays.toString(DEFAULT_URL_MAPPINGS));
      } else {
         if (!this.servletNames.isEmpty()) {
            builder.append(" servlets=").append(this.servletNames);
         }

         if (!this.urlPatterns.isEmpty()) {
            builder.append(" urls=").append(this.urlPatterns);
         }
      }

      builder.append(" order=").append(this.getOrder());
      return builder.toString();
   }
}
