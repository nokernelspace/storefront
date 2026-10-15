package org.springframework.web.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.Assert;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

public class DelegatingFilterProxy extends GenericFilterBean {
   private @Nullable String contextAttribute;
   private @Nullable WebApplicationContext webApplicationContext;
   private @Nullable String targetBeanName;
   private boolean targetFilterLifecycle = false;
   private volatile @Nullable Filter delegate;
   private final Lock delegateLock = new ReentrantLock();

   public DelegatingFilterProxy() {
   }

   public DelegatingFilterProxy(Filter delegate) {
      Assert.notNull(delegate, "Delegate Filter must not be null");
      this.delegate = delegate;
   }

   public DelegatingFilterProxy(String targetBeanName) {
      this(targetBeanName, null);
   }

   public DelegatingFilterProxy(String targetBeanName, @Nullable WebApplicationContext wac) {
      Assert.hasText(targetBeanName, "Target Filter bean name must not be null or empty");
      this.setTargetBeanName(targetBeanName);
      this.webApplicationContext = wac;
      if (wac != null) {
         this.setEnvironment(wac.getEnvironment());
      }
   }

   public void setContextAttribute(@Nullable String contextAttribute) {
      this.contextAttribute = contextAttribute;
   }

   public @Nullable String getContextAttribute() {
      return this.contextAttribute;
   }

   public void setTargetBeanName(@Nullable String targetBeanName) {
      this.targetBeanName = targetBeanName;
   }

   protected @Nullable String getTargetBeanName() {
      return this.targetBeanName;
   }

   public void setTargetFilterLifecycle(boolean targetFilterLifecycle) {
      this.targetFilterLifecycle = targetFilterLifecycle;
   }

   protected boolean isTargetFilterLifecycle() {
      return this.targetFilterLifecycle;
   }

   @Override
   protected void initFilterBean() throws ServletException {
      this.delegateLock.lock();

      try {
         if (this.delegate == null) {
            if (this.targetBeanName == null) {
               this.targetBeanName = this.getFilterName();
            }

            WebApplicationContext wac = this.findWebApplicationContext();
            if (wac != null) {
               this.delegate = this.initDelegate(wac);
            }
         }
      } finally {
         this.delegateLock.unlock();
      }
   }

   public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws ServletException, IOException {
      Filter delegateToUse = this.delegate;
      if (delegateToUse == null) {
         this.delegateLock.lock();

         try {
            delegateToUse = this.delegate;
            if (delegateToUse == null) {
               WebApplicationContext wac = this.findWebApplicationContext();
               if (wac == null) {
                  throw new IllegalStateException("No WebApplicationContext found: no ContextLoaderListener or DispatcherServlet registered?");
               }

               delegateToUse = this.initDelegate(wac);
            }

            this.delegate = delegateToUse;
         } finally {
            this.delegateLock.unlock();
         }
      }

      this.invokeDelegate(delegateToUse, request, response, filterChain);
   }

   @Override
   public void destroy() {
      Filter delegateToUse = this.delegate;
      if (delegateToUse != null) {
         this.destroyDelegate(delegateToUse);
      }
   }

   protected @Nullable WebApplicationContext findWebApplicationContext() {
      if (this.webApplicationContext != null) {
         if (this.webApplicationContext instanceof ConfigurableApplicationContext cac && !cac.isActive()) {
            cac.refresh();
         }

         return this.webApplicationContext;
      } else {
         String attrName = this.getContextAttribute();
         return attrName != null
            ? WebApplicationContextUtils.getWebApplicationContext(this.getServletContext(), attrName)
            : WebApplicationContextUtils.findWebApplicationContext(this.getServletContext());
      }
   }

   protected Filter initDelegate(WebApplicationContext wac) throws ServletException {
      String targetBeanName = this.getTargetBeanName();
      Assert.state(targetBeanName != null, "No target bean name set");
      Filter delegate = (Filter)wac.getBean(targetBeanName, Filter.class);
      if (this.isTargetFilterLifecycle()) {
         delegate.init(this.getFilterConfig());
      }

      return delegate;
   }

   protected void invokeDelegate(Filter delegate, ServletRequest request, ServletResponse response, FilterChain filterChain) throws ServletException, IOException {
      delegate.doFilter(request, response, filterChain);
   }

   protected void destroyDelegate(Filter delegate) {
      if (this.isTargetFilterLifecycle()) {
         delegate.destroy();
      }
   }
}
