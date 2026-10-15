package org.springframework.web.context.support;

import jakarta.servlet.ServletConfig;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.util.StringUtils;

public class ServletConfigPropertySource extends EnumerablePropertySource<ServletConfig> {
   public ServletConfigPropertySource(String name, ServletConfig servletConfig) {
      super(name, servletConfig);
   }

   public String[] getPropertyNames() {
      return StringUtils.toStringArray(((ServletConfig)this.source).getInitParameterNames());
   }

   public @Nullable String getProperty(String name) {
      return ((ServletConfig)this.source).getInitParameter(name);
   }
}
