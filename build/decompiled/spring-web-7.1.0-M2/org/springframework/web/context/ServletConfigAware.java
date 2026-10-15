package org.springframework.web.context;

import jakarta.servlet.ServletConfig;
import org.springframework.beans.factory.Aware;

public interface ServletConfigAware extends Aware {
   void setServletConfig(ServletConfig servletConfig);
}
