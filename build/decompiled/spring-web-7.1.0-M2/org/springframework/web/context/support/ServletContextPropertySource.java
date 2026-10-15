package org.springframework.web.context.support;

import jakarta.servlet.ServletContext;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.util.StringUtils;

public class ServletContextPropertySource extends EnumerablePropertySource<ServletContext> {
   public ServletContextPropertySource(String name, ServletContext servletContext) {
      super(name, servletContext);
   }

   public String[] getPropertyNames() {
      return StringUtils.toStringArray(((ServletContext)this.source).getInitParameterNames());
   }

   public @Nullable String getProperty(String name) {
      return ((ServletContext)this.source).getInitParameter(name);
   }
}
