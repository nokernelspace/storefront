package org.springframework.web.jsf.el;

import jakarta.el.ELContext;
import jakarta.el.ELException;
import jakarta.el.ELResolver;
import jakarta.el.PropertyNotWritableException;
import jakarta.faces.context.FacesContext;
import org.jspecify.annotations.Nullable;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.jsf.FacesContextUtils;

public class SpringBeanFacesELResolver extends ELResolver {
   public @Nullable Object getValue(ELContext elContext, @Nullable Object base, Object property) throws ELException {
      if (base == null) {
         String beanName = property.toString();
         WebApplicationContext wac = this.getWebApplicationContext(elContext);
         if (wac.containsBean(beanName)) {
            elContext.setPropertyResolved(true);
            return wac.getBean(beanName);
         }
      }

      return null;
   }

   public @Nullable Class<?> getType(ELContext elContext, @Nullable Object base, Object property) throws ELException {
      if (base == null) {
         String beanName = property.toString();
         WebApplicationContext wac = this.getWebApplicationContext(elContext);
         if (wac.containsBean(beanName)) {
            elContext.setPropertyResolved(true);
            return wac.getType(beanName);
         }
      }

      return null;
   }

   public void setValue(ELContext elContext, @Nullable Object base, Object property, Object value) throws ELException {
      if (base == null) {
         String beanName = property.toString();
         WebApplicationContext wac = this.getWebApplicationContext(elContext);
         if (wac.containsBean(beanName)) {
            if (value != wac.getBean(beanName)) {
               throw new PropertyNotWritableException("Variable '" + beanName + "' refers to a Spring bean which by definition is not writable");
            }

            elContext.setPropertyResolved(true);
         }
      }
   }

   public boolean isReadOnly(ELContext elContext, @Nullable Object base, Object property) throws ELException {
      if (base == null) {
         String beanName = property.toString();
         WebApplicationContext wac = this.getWebApplicationContext(elContext);
         if (wac.containsBean(beanName)) {
            return true;
         }
      }

      return false;
   }

   public Class<?> getCommonPropertyType(ELContext elContext, @Nullable Object base) {
      return Object.class;
   }

   protected WebApplicationContext getWebApplicationContext(ELContext elContext) {
      FacesContext facesContext = FacesContext.getCurrentInstance();
      return FacesContextUtils.getRequiredWebApplicationContext(facesContext);
   }
}
