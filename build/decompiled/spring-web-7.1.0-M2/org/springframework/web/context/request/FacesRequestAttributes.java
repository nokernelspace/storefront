package org.springframework.web.context.request;

import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import java.lang.reflect.Method;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.util.WebUtils;

public class FacesRequestAttributes implements RequestAttributes {
   private static final Log logger = LogFactory.getLog(FacesRequestAttributes.class);
   private final FacesContext facesContext;

   public FacesRequestAttributes(FacesContext facesContext) {
      Assert.notNull(facesContext, "FacesContext must not be null");
      this.facesContext = facesContext;
   }

   protected final FacesContext getFacesContext() {
      return this.facesContext;
   }

   protected final ExternalContext getExternalContext() {
      return this.getFacesContext().getExternalContext();
   }

   protected Map<String, Object> getAttributeMap(int scope) {
      return scope == 0 ? this.getExternalContext().getRequestMap() : this.getExternalContext().getSessionMap();
   }

   @Override
   public @Nullable Object getAttribute(String name, int scope) {
      return this.getAttributeMap(scope).get(name);
   }

   @Override
   public void setAttribute(String name, Object value, int scope) {
      this.getAttributeMap(scope).put(name, value);
   }

   @Override
   public void removeAttribute(String name, int scope) {
      this.getAttributeMap(scope).remove(name);
   }

   @Override
   public String[] getAttributeNames(int scope) {
      return StringUtils.toStringArray(this.getAttributeMap(scope).keySet());
   }

   @Override
   public void registerDestructionCallback(String name, Runnable callback, int scope) {
      if (logger.isWarnEnabled()) {
         logger.warn(
            "Could not register destruction callback ["
               + callback
               + "] for attribute '"
               + name
               + "' because FacesRequestAttributes does not support such callbacks"
         );
      }
   }

   @Override
   public @Nullable Object resolveReference(String key) {
      return switch (key) {
         case "request" -> this.getExternalContext().getRequest();
         case "session" -> this.getExternalContext().getSession(true);
         case "application" -> this.getExternalContext().getContext();
         case "requestScope" -> this.getExternalContext().getRequestMap();
         case "sessionScope" -> this.getExternalContext().getSessionMap();
         case "applicationScope" -> this.getExternalContext().getApplicationMap();
         case "facesContext" -> this.getFacesContext();
         case "cookie" -> this.getExternalContext().getRequestCookieMap();
         case "header" -> this.getExternalContext().getRequestHeaderMap();
         case "headerValues" -> this.getExternalContext().getRequestHeaderValuesMap();
         case "param" -> this.getExternalContext().getRequestParameterMap();
         case "paramValues" -> this.getExternalContext().getRequestParameterValuesMap();
         case "initParam" -> this.getExternalContext().getInitParameterMap();
         case "view" -> this.getFacesContext().getViewRoot();
         case "viewScope" -> this.getFacesContext().getViewRoot().getViewMap();
         case "flash" -> this.getExternalContext().getFlash();
         case "resource" -> this.getFacesContext().getApplication().getResourceHandler();
         default -> null;
      };
   }

   @Override
   public String getSessionId() {
      Object session = this.getExternalContext().getSession(true);

      try {
         Method getIdMethod = session.getClass().getMethod("getId");
         return String.valueOf(ReflectionUtils.invokeMethod(getIdMethod, session));
      } catch (NoSuchMethodException ex) {
         throw new IllegalStateException("Session object [" + session + "] does not have a getId() method");
      }
   }

   @Override
   public Object getSessionMutex() {
      ExternalContext externalContext = this.getExternalContext();
      Object session = externalContext.getSession(true);
      Object mutex = externalContext.getSessionMap().get(WebUtils.SESSION_MUTEX_ATTRIBUTE);
      if (mutex == null) {
         mutex = session != null ? session : externalContext;
      }

      return mutex;
   }
}
