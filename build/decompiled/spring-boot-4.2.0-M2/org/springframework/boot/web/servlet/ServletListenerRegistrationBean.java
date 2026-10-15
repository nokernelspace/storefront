package org.springframework.boot.web.servlet;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextAttributeListener;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletRequestAttributeListener;
import jakarta.servlet.ServletRequestListener;
import jakarta.servlet.http.HttpSessionAttributeListener;
import jakarta.servlet.http.HttpSessionIdListener;
import jakarta.servlet.http.HttpSessionListener;
import java.util.Collections;
import java.util.EventListener;
import java.util.HashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

public class ServletListenerRegistrationBean<T extends EventListener> extends RegistrationBean {
   private static final Set<Class<?>> SUPPORTED_TYPES;
   private @Nullable T listener;

   public ServletListenerRegistrationBean() {
   }

   public ServletListenerRegistrationBean(T listener) {
      Assert.notNull(listener, "'listener' must not be null");
      Assert.isTrue(isSupportedType(listener), "'listener' is not of a supported type");
      this.listener = listener;
   }

   public void setListener(T listener) {
      Assert.notNull(listener, "'listener' must not be null");
      Assert.isTrue(isSupportedType(listener), "'listener' is not of a supported type");
      this.listener = listener;
   }

   public @Nullable T getListener() {
      return this.listener;
   }

   @Override
   protected String getDescription() {
      Assert.notNull(this.listener, "'listener' must not be null");
      return "listener " + this.listener;
   }

   @Override
   protected void register(String description, ServletContext servletContext) {
      try {
         servletContext.addListener(this.listener);
      } catch (RuntimeException ex) {
         throw new IllegalStateException("Failed to add listener '" + this.listener + "' to servlet context", ex);
      }
   }

   public static boolean isSupportedType(EventListener listener) {
      for (Class<?> type : SUPPORTED_TYPES) {
         if (ClassUtils.isAssignableValue(type, listener)) {
            return true;
         }
      }

      return false;
   }

   public static Set<Class<?>> getSupportedTypes() {
      return SUPPORTED_TYPES;
   }

   static {
      Set<Class<?>> types = new HashSet<>();
      types.add(ServletContextAttributeListener.class);
      types.add(ServletRequestListener.class);
      types.add(ServletRequestAttributeListener.class);
      types.add(HttpSessionAttributeListener.class);
      types.add(HttpSessionIdListener.class);
      types.add(HttpSessionListener.class);
      types.add(ServletContextListener.class);
      SUPPORTED_TYPES = Collections.unmodifiableSet(types);
   }
}
