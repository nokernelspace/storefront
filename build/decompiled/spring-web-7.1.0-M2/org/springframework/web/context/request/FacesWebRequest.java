package org.springframework.web.context.request;

import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import java.security.Principal;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

public class FacesWebRequest extends FacesRequestAttributes implements NativeWebRequest {
   public FacesWebRequest(FacesContext facesContext) {
      super(facesContext);
   }

   @Override
   public Object getNativeRequest() {
      return this.getExternalContext().getRequest();
   }

   @Override
   public Object getNativeResponse() {
      return this.getExternalContext().getResponse();
   }

   @Override
   public <T> @Nullable T getNativeRequest(@Nullable Class<T> requiredType) {
      if (requiredType != null) {
         Object request = this.getExternalContext().getRequest();
         if (requiredType.isInstance(request)) {
            return (T)request;
         }
      }

      return null;
   }

   @Override
   public <T> @Nullable T getNativeResponse(@Nullable Class<T> requiredType) {
      if (requiredType != null) {
         Object response = this.getExternalContext().getResponse();
         if (requiredType.isInstance(response)) {
            return (T)response;
         }
      }

      return null;
   }

   @Override
   public @Nullable String getHeader(String headerName) {
      return (String)this.getExternalContext().getRequestHeaderMap().get(headerName);
   }

   @Override
   public String @Nullable [] getHeaderValues(String headerName) {
      return (String[])this.getExternalContext().getRequestHeaderValuesMap().get(headerName);
   }

   @Override
   public Iterator<String> getHeaderNames() {
      return this.getExternalContext().getRequestHeaderMap().keySet().iterator();
   }

   @Override
   public @Nullable String getParameter(String paramName) {
      return (String)this.getExternalContext().getRequestParameterMap().get(paramName);
   }

   @Override
   public Iterator<String> getParameterNames() {
      return this.getExternalContext().getRequestParameterNames();
   }

   @Override
   public String @Nullable [] getParameterValues(String paramName) {
      return (String[])this.getExternalContext().getRequestParameterValuesMap().get(paramName);
   }

   @Override
   public Map<String, String[]> getParameterMap() {
      return this.getExternalContext().getRequestParameterValuesMap();
   }

   @Override
   public Locale getLocale() {
      return this.getFacesContext().getExternalContext().getRequestLocale();
   }

   @Override
   public String getContextPath() {
      return this.getFacesContext().getExternalContext().getRequestContextPath();
   }

   @Override
   public @Nullable String getRemoteUser() {
      return this.getFacesContext().getExternalContext().getRemoteUser();
   }

   @Override
   public @Nullable Principal getUserPrincipal() {
      return this.getFacesContext().getExternalContext().getUserPrincipal();
   }

   @Override
   public boolean isUserInRole(String role) {
      return this.getFacesContext().getExternalContext().isUserInRole(role);
   }

   @Override
   public boolean isSecure() {
      return false;
   }

   @Override
   public boolean checkNotModified(long lastModifiedTimestamp) {
      return false;
   }

   @Override
   public boolean checkNotModified(@Nullable String eTag) {
      return false;
   }

   @Override
   public boolean checkNotModified(@Nullable String etag, long lastModifiedTimestamp) {
      return false;
   }

   @Override
   public String getDescription(boolean includeClientInfo) {
      ExternalContext externalContext = this.getExternalContext();
      StringBuilder sb = new StringBuilder();
      sb.append("context=").append(externalContext.getRequestContextPath());
      if (includeClientInfo) {
         Object session = externalContext.getSession(false);
         if (session != null) {
            sb.append(";session=").append(this.getSessionId());
         }

         String user = externalContext.getRemoteUser();
         if (StringUtils.hasLength(user)) {
            sb.append(";user=").append(user);
         }
      }

      return sb.toString();
   }

   @Override
   public String toString() {
      return "FacesWebRequest: " + this.getDescription(true);
   }
}
