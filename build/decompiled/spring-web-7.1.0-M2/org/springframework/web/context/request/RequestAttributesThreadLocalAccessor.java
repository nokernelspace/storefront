package org.springframework.web.context.request;

import io.micrometer.context.ThreadLocalAccessor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public class RequestAttributesThreadLocalAccessor implements ThreadLocalAccessor<RequestAttributes> {
   public static final String KEY = RequestAttributesThreadLocalAccessor.class.getName() + ".KEY";

   public Object key() {
      return KEY;
   }

   public @Nullable RequestAttributes getValue() {
      RequestAttributes request = RequestContextHolder.getRequestAttributes();
      if (request instanceof ServletRequestAttributes sra && !(sra instanceof RequestAttributesThreadLocalAccessor.SnapshotServletRequestAttributes)) {
         request = new RequestAttributesThreadLocalAccessor.SnapshotServletRequestAttributes(sra);
      }

      return request;
   }

   public void setValue(RequestAttributes value) {
      RequestContextHolder.setRequestAttributes(value);
   }

   public void setValue() {
      RequestContextHolder.resetRequestAttributes();
   }

   private static final class SnapshotServletRequestAttributes extends ServletRequestAttributes {
      private final ServletRequestAttributes delegate;
      private final Map<String, Object> attributeMap;

      public SnapshotServletRequestAttributes(ServletRequestAttributes requestAttributes) {
         super(requestAttributes.getRequest(), requestAttributes.getResponse());
         this.delegate = requestAttributes;
         this.attributeMap = getAttributes(requestAttributes.getRequest());
      }

      private static Map<String, Object> getAttributes(HttpServletRequest request) {
         Map<String, Object> map = new HashMap<>();
         Enumeration<String> names = request.getAttributeNames();

         while (names.hasMoreElements()) {
            String name = names.nextElement();
            map.put(name, request.getAttribute(name));
         }

         return map;
      }

      @Override
      public @Nullable Object getAttribute(String name, int scope) {
         if (scope == 0 && !this.delegate.isRequestActive()) {
            return this.attributeMap.get(name);
         }

         try {
            return this.delegate.getAttribute(name, scope);
         } catch (IllegalStateException ex) {
            if (scope == 0) {
               return this.attributeMap.get(name);
            } else {
               throw ex;
            }
         }
      }

      @Override
      public String[] getAttributeNames(int scope) {
         if (scope == 0 && !this.delegate.isRequestActive()) {
            return this.attributeMap.keySet().toArray(new String[0]);
         }

         try {
            return this.delegate.getAttributeNames(scope);
         } catch (IllegalStateException ex) {
            if (scope == 0) {
               return this.attributeMap.keySet().toArray(new String[0]);
            } else {
               throw ex;
            }
         }
      }

      @Override
      public void setAttribute(String name, Object value, int scope) {
         this.delegate.setAttribute(name, value, scope);
      }

      @Override
      public void removeAttribute(String name, int scope) {
         this.delegate.removeAttribute(name, scope);
      }
   }
}
