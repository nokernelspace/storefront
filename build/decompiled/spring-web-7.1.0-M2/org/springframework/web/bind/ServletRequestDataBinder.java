package org.springframework.web.bind;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import java.lang.reflect.Array;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.DataBinder.ValueResolver;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartRequest;
import org.springframework.web.multipart.support.StandardServletPartUtils;
import org.springframework.web.util.WebUtils;

public class ServletRequestDataBinder extends WebDataBinder {
   public ServletRequestDataBinder(@Nullable Object target) {
      super(target);
   }

   public ServletRequestDataBinder(@Nullable Object target, String objectName) {
      super(target, objectName);
   }

   public void construct(ServletRequest request) {
      this.construct(this.createValueResolver(request));
   }

   protected ServletRequestDataBinder.ServletRequestValueResolver createValueResolver(ServletRequest request) {
      return new ServletRequestDataBinder.ServletRequestValueResolver(request, this);
   }

   protected boolean shouldConstructArgument(MethodParameter param) {
      Class<?> type = param.nestedIfOptional().getNestedParameterType();
      return super.shouldConstructArgument(param) && !MultipartFile.class.isAssignableFrom(type) && !Part.class.isAssignableFrom(type);
   }

   public void bind(ServletRequest request) {
      if (!this.shouldNotBindPropertyValues()) {
         MutablePropertyValues mpvs = new ServletRequestParameterPropertyValues(request);
         MultipartRequest multipartRequest = WebUtils.getNativeRequest(request, MultipartRequest.class);
         if (multipartRequest != null) {
            this.bindMultipart(multipartRequest.getMultiFileMap(), mpvs);
         } else if (isFormDataPost(request)) {
            HttpServletRequest httpServletRequest = WebUtils.getNativeRequest(request, HttpServletRequest.class);
            if (httpServletRequest != null && HttpMethod.POST.matches(httpServletRequest.getMethod())) {
               StandardServletPartUtils.bindParts(httpServletRequest, mpvs, this.isBindEmptyMultipartFiles());
            }
         }

         this.addBindValues(mpvs, request);
         this.doBind(mpvs);
      }
   }

   private static boolean isFormDataPost(ServletRequest request) {
      return StringUtils.startsWithIgnoreCase(request.getContentType(), "multipart/form-data");
   }

   protected void addBindValues(MutablePropertyValues mpvs, ServletRequest request) {
   }

   public void closeNoCatch() throws ServletRequestBindingException {
      if (this.getBindingResult().hasErrors()) {
         throw new ServletRequestBindingException(
            "Errors binding onto object '" + this.getBindingResult().getObjectName() + "'", new BindException(this.getBindingResult())
         );
      }
   }

   public static ValueResolver valueResolver(ServletRequest request, WebDataBinder binder) {
      return new ServletRequestDataBinder.ServletRequestValueResolver(request, binder);
   }

   protected static class ServletRequestValueResolver implements ValueResolver {
      private final ServletRequest request;
      private final WebDataBinder dataBinder;
      private @Nullable Set<String> parameterNames;

      protected ServletRequestValueResolver(ServletRequest request, WebDataBinder dataBinder) {
         this.request = request;
         this.dataBinder = dataBinder;
      }

      protected ServletRequest getRequest() {
         return this.request;
      }

      public final @Nullable Object resolveValue(String name, Class<?> paramType) {
         Object value = this.getRequestParameter(name, paramType);
         if (value == null) {
            value = this.dataBinder.resolvePrefixValue(name, paramType, this::getRequestParameter);
         }

         if (value == null) {
            value = this.getMultipartValue(name);
         }

         return value;
      }

      protected @Nullable Object getRequestParameter(String name, Class<?> type) {
         Object value = this.request.getParameterValues(name);
         return ObjectUtils.isArray(value) && Array.getLength(value) == 1 ? Array.get(value, 0) : value;
      }

      private @Nullable Object getMultipartValue(String name) {
         MultipartRequest multipartRequest = WebUtils.getNativeRequest(this.request, MultipartRequest.class);
         if (multipartRequest != null) {
            List<MultipartFile> files = multipartRequest.getFiles(name);
            if (!files.isEmpty()) {
               return files.size() == 1 ? files.get(0) : files;
            }
         } else if (ServletRequestDataBinder.isFormDataPost(this.request)) {
            HttpServletRequest httpRequest = WebUtils.getNativeRequest(this.request, HttpServletRequest.class);
            if (httpRequest != null && HttpMethod.POST.matches(httpRequest.getMethod())) {
               List<Part> parts = StandardServletPartUtils.getParts(httpRequest, name);
               if (!parts.isEmpty()) {
                  return parts.size() == 1 ? parts.get(0) : parts;
               }
            }
         }

         return null;
      }

      public Set<String> getNames() {
         if (this.parameterNames == null) {
            this.parameterNames = this.initParameterNames(this.request);
         }

         return this.parameterNames;
      }

      protected Set<String> initParameterNames(ServletRequest request) {
         Set<String> set = new LinkedHashSet<>();
         Enumeration<String> enumeration = request.getParameterNames();

         while (enumeration.hasMoreElements()) {
            set.add(enumeration.nextElement());
         }

         return set;
      }
   }
}
