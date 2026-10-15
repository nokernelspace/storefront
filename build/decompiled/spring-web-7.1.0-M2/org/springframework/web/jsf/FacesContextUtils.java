package org.springframework.web.jsf;

import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.WebUtils;

public abstract class FacesContextUtils {
   public static @Nullable WebApplicationContext getWebApplicationContext(FacesContext fc) {
      Assert.notNull(fc, "FacesContext must not be null");
      Object attr = fc.getExternalContext().getApplicationMap().get(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
      if (attr == null) {
         return null;
      } else if (attr instanceof RuntimeException runtimeException) {
         throw runtimeException;
      } else if (attr instanceof Error error) {
         throw error;
      } else if (attr instanceof WebApplicationContext wac) {
         return wac;
      } else {
         throw new IllegalStateException("Root context attribute is not of type WebApplicationContext: " + attr);
      }
   }

   public static WebApplicationContext getRequiredWebApplicationContext(FacesContext fc) throws IllegalStateException {
      WebApplicationContext wac = getWebApplicationContext(fc);
      if (wac == null) {
         throw new IllegalStateException("No WebApplicationContext found: no ContextLoaderListener registered?");
      } else {
         return wac;
      }
   }

   public static @Nullable Object getSessionMutex(FacesContext fc) {
      Assert.notNull(fc, "FacesContext must not be null");
      ExternalContext ec = fc.getExternalContext();
      Object mutex = ec.getSessionMap().get(WebUtils.SESSION_MUTEX_ATTRIBUTE);
      if (mutex == null) {
         mutex = ec.getSession(true);
      }

      return mutex;
   }
}
