package org.springframework.web.context.request;

import jakarta.faces.context.FacesContext;
import org.jspecify.annotations.Nullable;
import org.springframework.core.NamedInheritableThreadLocal;
import org.springframework.core.NamedThreadLocal;
import org.springframework.util.ClassUtils;

public abstract class RequestContextHolder {
   private static final boolean JSF_PRESENT = ClassUtils.isPresent("jakarta.faces.context.FacesContext", RequestContextHolder.class.getClassLoader());
   private static final ThreadLocal<RequestAttributes> requestAttributesHolder = new NamedThreadLocal("Request attributes");
   private static final ThreadLocal<RequestAttributes> inheritableRequestAttributesHolder = new NamedInheritableThreadLocal("Request context");

   public static void resetRequestAttributes() {
      requestAttributesHolder.remove();
      inheritableRequestAttributesHolder.remove();
   }

   public static void setRequestAttributes(@Nullable RequestAttributes attributes) {
      setRequestAttributes(attributes, false);
   }

   public static void setRequestAttributes(@Nullable RequestAttributes attributes, boolean inheritable) {
      if (attributes == null) {
         resetRequestAttributes();
      } else if (inheritable) {
         inheritableRequestAttributesHolder.set(attributes);
         requestAttributesHolder.remove();
      } else {
         requestAttributesHolder.set(attributes);
         inheritableRequestAttributesHolder.remove();
      }
   }

   public static @Nullable RequestAttributes getRequestAttributes() {
      RequestAttributes attributes = requestAttributesHolder.get();
      if (attributes == null) {
         attributes = inheritableRequestAttributesHolder.get();
      }

      return attributes;
   }

   public static RequestAttributes currentRequestAttributes() throws IllegalStateException {
      RequestAttributes attributes = getRequestAttributes();
      if (attributes == null) {
         if (JSF_PRESENT) {
            attributes = RequestContextHolder.FacesRequestAttributesFactory.getFacesRequestAttributes();
         }

         if (attributes == null) {
            throw new IllegalStateException(
               "No thread-bound request found: Are you referring to request attributes outside of an actual web request, or processing a request outside of the originally receiving thread? If you are actually operating within a web request and still receive this message, your code is probably running outside of DispatcherServlet: In this case, use RequestContextListener or RequestContextFilter to expose the current request."
            );
         }
      }

      return attributes;
   }

   private static class FacesRequestAttributesFactory {
      public static @Nullable RequestAttributes getFacesRequestAttributes() {
         try {
            FacesContext facesContext = FacesContext.getCurrentInstance();
            return facesContext != null ? new FacesRequestAttributes(facesContext) : null;
         } catch (NoClassDefFoundError err) {
            return null;
         }
      }
   }
}
