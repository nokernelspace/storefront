package org.springframework.boot.web.servlet;

import jakarta.servlet.Filter;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public class FilterRegistrationBean<T extends Filter> extends AbstractFilterRegistrationBean<T> {
   private @Nullable T filter;

   public FilterRegistrationBean() {
      super();
   }

   public FilterRegistrationBean(T filter, ServletRegistrationBean<?>... servletRegistrationBeans) {
      super(servletRegistrationBeans);
      Assert.notNull(filter, "'filter' must not be null");
      this.filter = filter;
   }

   @Override
   public @Nullable T getFilter() {
      return this.filter;
   }

   public void setFilter(T filter) {
      Assert.notNull(filter, "'filter' must not be null");
      this.filter = filter;
   }
}
