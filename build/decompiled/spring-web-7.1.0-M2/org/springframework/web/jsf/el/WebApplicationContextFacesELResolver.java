package org.springframework.web.jsf.el;

import jakarta.el.ELContext;
import jakarta.el.ELException;
import jakarta.el.ELResolver;
import jakarta.faces.context.FacesContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.jsf.FacesContextUtils;

public class WebApplicationContextFacesELResolver extends ELResolver {
   public static final String WEB_APPLICATION_CONTEXT_VARIABLE_NAME = "webApplicationContext";
   protected final Log logger = LogFactory.getLog(this.getClass());

   public @Nullable Object getValue(ELContext elContext, @Nullable Object base, Object property) throws ELException {
      if (base != null) {
         if (base instanceof WebApplicationContext wac) {
            String beanName = property.toString();
            if (this.logger.isTraceEnabled()) {
               this.logger.trace("Attempting to resolve property '" + beanName + "' in root WebApplicationContext");
            }

            if (wac.containsBean(beanName)) {
               if (this.logger.isDebugEnabled()) {
                  this.logger.debug("Successfully resolved property '" + beanName + "' in root WebApplicationContext");
               }

               elContext.setPropertyResolved(true);

               try {
                  return wac.getBean(beanName);
               } catch (BeansException ex) {
                  throw new ELException(ex);
               }
            }

            return null;
         }
      } else if ("webApplicationContext".equals(property)) {
         elContext.setPropertyResolved(true);
         return this.getWebApplicationContext(elContext);
      }

      return null;
   }

   public @Nullable Class<?> getType(ELContext elContext, @Nullable Object base, Object property) throws ELException {
      if (base != null) {
         if (base instanceof WebApplicationContext wac) {
            String beanName = property.toString();
            if (this.logger.isDebugEnabled()) {
               this.logger.debug("Attempting to resolve property '" + beanName + "' in root WebApplicationContext");
            }

            if (wac.containsBean(beanName)) {
               if (this.logger.isDebugEnabled()) {
                  this.logger.debug("Successfully resolved property '" + beanName + "' in root WebApplicationContext");
               }

               elContext.setPropertyResolved(true);

               try {
                  return wac.getType(beanName);
               } catch (BeansException ex) {
                  throw new ELException(ex);
               }
            }

            return null;
         }
      } else if ("webApplicationContext".equals(property)) {
         elContext.setPropertyResolved(true);
         return WebApplicationContext.class;
      }

      return null;
   }

   public void setValue(ELContext elContext, Object base, Object property, Object value) throws ELException {
   }

   public boolean isReadOnly(ELContext elContext, Object base, Object property) throws ELException {
      if (base instanceof WebApplicationContext) {
         elContext.setPropertyResolved(true);
         return true;
      } else {
         return false;
      }
   }

   public Class<?> getCommonPropertyType(ELContext elContext, Object base) {
      return Object.class;
   }

   protected @Nullable WebApplicationContext getWebApplicationContext(ELContext elContext) {
      FacesContext facesContext = FacesContext.getCurrentInstance();
      return FacesContextUtils.getRequiredWebApplicationContext(facesContext);
   }
}
