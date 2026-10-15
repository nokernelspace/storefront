package org.springframework.boot.web.servlet;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRegistration.Dynamic;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public class ServletRegistrationBean<T extends Servlet> extends DynamicRegistrationBean<Dynamic> {
   private static final String[] DEFAULT_MAPPINGS = new String[]{"/*"};
   private @Nullable T servlet;
   private Set<String> urlMappings = new LinkedHashSet<>();
   private boolean alwaysMapUrl = true;
   private int loadOnStartup = -1;
   private @Nullable MultipartConfigElement multipartConfig;

   public ServletRegistrationBean() {
   }

   public ServletRegistrationBean(T servlet, String... urlMappings) {
      this(servlet, true, urlMappings);
   }

   public ServletRegistrationBean(T servlet, boolean alwaysMapUrl, String... urlMappings) {
      Assert.notNull(servlet, "'servlet' must not be null");
      Assert.notNull(urlMappings, "'urlMappings' must not be null");
      this.servlet = servlet;
      this.alwaysMapUrl = alwaysMapUrl;
      this.urlMappings.addAll(Arrays.asList(urlMappings));
   }

   public void setServlet(T servlet) {
      Assert.notNull(servlet, "'servlet' must not be null");
      this.servlet = servlet;
   }

   public @Nullable T getServlet() {
      return this.servlet;
   }

   public void setUrlMappings(Collection<String> urlMappings) {
      Assert.notNull(urlMappings, "'urlMappings' must not be null");
      this.urlMappings = new LinkedHashSet<>(urlMappings);
   }

   public Collection<String> getUrlMappings() {
      return this.urlMappings;
   }

   public void addUrlMappings(String... urlMappings) {
      Assert.notNull(urlMappings, "'urlMappings' must not be null");
      this.urlMappings.addAll(Arrays.asList(urlMappings));
   }

   public void setLoadOnStartup(int loadOnStartup) {
      this.loadOnStartup = loadOnStartup;
   }

   public void setMultipartConfig(@Nullable MultipartConfigElement multipartConfig) {
      this.multipartConfig = multipartConfig;
   }

   public @Nullable MultipartConfigElement getMultipartConfig() {
      return this.multipartConfig;
   }

   @Override
   protected String getDescription() {
      Assert.state(this.servlet != null, "Unable to return description for null servlet");
      return "servlet " + this.getServletName();
   }

   protected Dynamic addRegistration(String description, ServletContext servletContext) {
      String name = this.getServletName();
      return servletContext.addServlet(name, this.servlet);
   }

   protected void configure(Dynamic registration) {
      super.configure(registration);
      String[] urlMapping = StringUtils.toStringArray(this.urlMappings);
      if (urlMapping.length == 0 && this.alwaysMapUrl) {
         urlMapping = DEFAULT_MAPPINGS;
      }

      if (!ObjectUtils.isEmpty(urlMapping)) {
         registration.addMapping(urlMapping);
      }

      registration.setLoadOnStartup(this.loadOnStartup);
      if (this.multipartConfig != null) {
         registration.setMultipartConfig(this.multipartConfig);
      }
   }

   public String getServletName() {
      return this.getOrDeduceName(this.servlet);
   }

   @Override
   public String toString() {
      return this.getServletName() + " urls=" + this.getUrlMappings();
   }
}
