package org.springframework.web.bind.support;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartRequest;
import org.springframework.web.multipart.support.StandardServletPartUtils;

public class WebRequestDataBinder extends WebDataBinder {
   public WebRequestDataBinder(@Nullable Object target) {
      super(target);
   }

   public WebRequestDataBinder(@Nullable Object target, String objectName) {
      super(target, objectName);
   }

   public void construct(WebRequest request) {
      if (request instanceof NativeWebRequest nativeRequest) {
         ServletRequest servletRequest = nativeRequest.getNativeRequest(ServletRequest.class);
         if (servletRequest != null) {
            this.construct(ServletRequestDataBinder.valueResolver(servletRequest, this));
         }
      }
   }

   protected boolean shouldConstructArgument(MethodParameter param) {
      Class<?> type = param.nestedIfOptional().getNestedParameterType();
      return super.shouldConstructArgument(param) && !MultipartFile.class.isAssignableFrom(type) && !Part.class.isAssignableFrom(type);
   }

   public void bind(WebRequest request) {
      if (!this.shouldNotBindPropertyValues()) {
         MutablePropertyValues mpvs = new MutablePropertyValues(request.getParameterMap());
         if (request instanceof NativeWebRequest nativeRequest) {
            MultipartRequest multipartRequest = nativeRequest.getNativeRequest(MultipartRequest.class);
            if (multipartRequest != null) {
               this.bindMultipart(multipartRequest.getMultiFileMap(), mpvs);
            } else if (StringUtils.startsWithIgnoreCase(request.getHeader("Content-Type"), "multipart/form-data")) {
               HttpServletRequest servletRequest = nativeRequest.getNativeRequest(HttpServletRequest.class);
               if (servletRequest != null && HttpMethod.POST.matches(servletRequest.getMethod())) {
                  StandardServletPartUtils.bindParts(servletRequest, mpvs, this.isBindEmptyMultipartFiles());
               }
            }
         }

         this.doBind(mpvs);
      }
   }

   public void closeNoCatch() throws BindException {
      if (this.getBindingResult().hasErrors()) {
         throw new BindException(this.getBindingResult());
      }
   }
}
