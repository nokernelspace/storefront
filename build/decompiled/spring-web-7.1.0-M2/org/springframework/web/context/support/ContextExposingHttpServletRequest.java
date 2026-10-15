package org.springframework.web.context.support;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.HashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.context.WebApplicationContext;

public class ContextExposingHttpServletRequest extends HttpServletRequestWrapper {
   private final WebApplicationContext webApplicationContext;
   private final @Nullable Set<String> exposedContextBeanNames;
   private @Nullable Set<String> explicitAttributes;

   public ContextExposingHttpServletRequest(HttpServletRequest originalRequest, WebApplicationContext context) {
      this(originalRequest, context, null);
   }

   public ContextExposingHttpServletRequest(HttpServletRequest originalRequest, WebApplicationContext context, @Nullable Set<String> exposedContextBeanNames) {
      super(originalRequest);
      Assert.notNull(context, "WebApplicationContext must not be null");
      this.webApplicationContext = context;
      this.exposedContextBeanNames = exposedContextBeanNames;
   }

   public final WebApplicationContext getWebApplicationContext() {
      return this.webApplicationContext;
   }

   public @Nullable Object getAttribute(String name) {
      return (this.explicitAttributes == null || !this.explicitAttributes.contains(name))
            && (this.exposedContextBeanNames == null || this.exposedContextBeanNames.contains(name))
            && this.webApplicationContext.containsBean(name)
         ? this.webApplicationContext.getBean(name)
         : super.getAttribute(name);
   }

   public void setAttribute(String name, Object value) {
      super.setAttribute(name, value);
      if (this.explicitAttributes == null) {
         this.explicitAttributes = new HashSet<>(8);
      }

      this.explicitAttributes.add(name);
   }
}
