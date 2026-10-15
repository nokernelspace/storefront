package org.springframework.web.multipart.support;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;

public class StandardServletMultipartResolver implements MultipartResolver {
   private boolean resolveLazily = false;
   private boolean strictServletCompliance = false;

   public void setResolveLazily(boolean resolveLazily) {
      this.resolveLazily = resolveLazily;
   }

   public void setStrictServletCompliance(boolean strictServletCompliance) {
      this.strictServletCompliance = strictServletCompliance;
   }

   @Override
   public boolean isMultipart(HttpServletRequest request) {
      return StringUtils.startsWithIgnoreCase(request.getContentType(), this.strictServletCompliance ? "multipart/form-data" : "multipart/");
   }

   @Override
   public MultipartHttpServletRequest resolveMultipart(HttpServletRequest request) throws MultipartException {
      return new StandardMultipartHttpServletRequest(request, this.resolveLazily);
   }

   @Override
   public void cleanupMultipart(MultipartHttpServletRequest request) {
      if (!(request instanceof AbstractMultipartHttpServletRequest abstractMultipartHttpServletRequest && !abstractMultipartHttpServletRequest.isResolved())) {
         try {
            for (Part part : request.getParts()) {
               part.delete();
            }
         } catch (Throwable ex) {
            LogFactory.getLog(this.getClass()).warn("Failed to perform cleanup of multipart items", ex);
         }
      }
   }
}
