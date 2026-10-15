package org.springframework.web.context.support;

import jakarta.servlet.ServletContext;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.web.context.ServletContextAware;

public class ServletContextAttributeExporter implements ServletContextAware {
   protected final Log logger = LogFactory.getLog(this.getClass());
   private @Nullable Map<String, Object> attributes;

   public void setAttributes(Map<String, Object> attributes) {
      this.attributes = attributes;
   }

   @Override
   public void setServletContext(ServletContext servletContext) {
      if (this.attributes != null) {
         for (Entry<String, Object> entry : this.attributes.entrySet()) {
            String attributeName = entry.getKey();
            if (this.logger.isDebugEnabled() && servletContext.getAttribute(attributeName) != null) {
               this.logger.debug("Replacing existing ServletContext attribute with name '" + attributeName + "'");
            }

            servletContext.setAttribute(attributeName, entry.getValue());
            if (this.logger.isTraceEnabled()) {
               this.logger.trace("Exported ServletContext attribute with name '" + attributeName + "'");
            }
         }
      }
   }
}
