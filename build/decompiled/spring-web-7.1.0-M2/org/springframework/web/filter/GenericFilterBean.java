package org.springframework.web.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceEditor;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.context.support.ServletContextResourceLoader;
import org.springframework.web.context.support.StandardServletEnvironment;

public abstract class GenericFilterBean
   implements Filter,
   BeanNameAware,
   EnvironmentAware,
   EnvironmentCapable,
   ServletContextAware,
   InitializingBean,
   DisposableBean {
   protected final Log logger = LogFactory.getLog(this.getClass());
   private @Nullable String beanName;
   private @Nullable Environment environment;
   private @Nullable ServletContext servletContext;
   private @Nullable FilterConfig filterConfig;
   private final Set<String> requiredProperties = new HashSet<>(4);

   public void setBeanName(String beanName) {
      this.beanName = beanName;
   }

   public void setEnvironment(Environment environment) {
      this.environment = environment;
   }

   public Environment getEnvironment() {
      if (this.environment == null) {
         this.environment = this.createEnvironment();
      }

      return this.environment;
   }

   protected Environment createEnvironment() {
      return new StandardServletEnvironment();
   }

   @Override
   public void setServletContext(ServletContext servletContext) {
      this.servletContext = servletContext;
   }

   public void afterPropertiesSet() throws ServletException {
      this.initFilterBean();
   }

   public void destroy() {
   }

   protected final void addRequiredProperty(String property) {
      this.requiredProperties.add(property);
   }

   public final void init(FilterConfig filterConfig) throws ServletException {
      Assert.notNull(filterConfig, "FilterConfig must not be null");
      this.filterConfig = filterConfig;
      PropertyValues pvs = new GenericFilterBean.FilterConfigPropertyValues(filterConfig, this.requiredProperties);
      if (!pvs.isEmpty()) {
         try {
            BeanWrapper bw = PropertyAccessorFactory.forBeanPropertyAccess(this);
            ResourceLoader resourceLoader = new ServletContextResourceLoader(filterConfig.getServletContext());
            Environment env = this.environment;
            if (env == null) {
               env = new StandardServletEnvironment();
            }

            bw.registerCustomEditor(Resource.class, new ResourceEditor(resourceLoader, env));
            this.initBeanWrapper(bw);
            bw.setPropertyValues(pvs, true);
         } catch (BeansException ex) {
            String msg = "Failed to set bean properties on filter '" + filterConfig.getFilterName() + "': " + ex.getMessage();
            this.logger.error(msg, ex);
            throw new ServletException(msg, ex);
         }
      }

      this.initFilterBean();
      if (this.logger.isDebugEnabled()) {
         this.logger.debug("Filter '" + filterConfig.getFilterName() + "' configured for use");
      }
   }

   protected void initBeanWrapper(BeanWrapper bw) throws BeansException {
   }

   protected void initFilterBean() throws ServletException {
   }

   public @Nullable FilterConfig getFilterConfig() {
      return this.filterConfig;
   }

   protected @Nullable String getFilterName() {
      return this.filterConfig != null ? this.filterConfig.getFilterName() : this.beanName;
   }

   protected ServletContext getServletContext() {
      if (this.filterConfig != null) {
         return this.filterConfig.getServletContext();
      } else if (this.servletContext != null) {
         return this.servletContext;
      } else {
         throw new IllegalStateException("No ServletContext");
      }
   }

   private static class FilterConfigPropertyValues extends MutablePropertyValues {
      public FilterConfigPropertyValues(FilterConfig config, Set<String> requiredProperties) throws ServletException {
         Set<String> missingProps = !CollectionUtils.isEmpty(requiredProperties) ? new HashSet<>(requiredProperties) : null;
         Enumeration<String> paramNames = config.getInitParameterNames();

         while (paramNames.hasMoreElements()) {
            String property = paramNames.nextElement();
            Object value = config.getInitParameter(property);
            this.addPropertyValue(new PropertyValue(property, value));
            if (missingProps != null) {
               missingProps.remove(property);
            }
         }

         if (!CollectionUtils.isEmpty(missingProps)) {
            throw new ServletException(
               "Initialization from FilterConfig for filter '"
                  + config.getFilterName()
                  + "' failed; the following required properties were missing: "
                  + StringUtils.collectionToDelimitedString(missingProps, ", ")
            );
         }
      }
   }
}
