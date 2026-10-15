package org.springframework.web.multipart.support;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

public class DefaultMultipartHttpServletRequest extends AbstractMultipartHttpServletRequest {
   private static final String CONTENT_TYPE = "Content-Type";
   private @Nullable Map<String, String[]> multipartParameters;
   private @Nullable Map<String, String> multipartParameterContentTypes;

   public DefaultMultipartHttpServletRequest(
      HttpServletRequest request, MultiValueMap<String, MultipartFile> mpFiles, Map<String, String[]> mpParams, Map<String, String> mpParamContentTypes
   ) {
      super(request);
      this.setMultipartFiles(mpFiles);
      this.setMultipartParameters(mpParams);
      this.setMultipartParameterContentTypes(mpParamContentTypes);
   }

   public DefaultMultipartHttpServletRequest(HttpServletRequest request) {
      super(request);
   }

   public @Nullable String getParameter(String name) {
      String[] values = this.getMultipartParameters().get(name);
      if (values != null) {
         return values.length > 0 ? values[0] : null;
      } else {
         return super.getParameter(name);
      }
   }

   public String[] getParameterValues(String name) {
      String[] parameterValues = super.getParameterValues(name);
      String[] mpValues = this.getMultipartParameters().get(name);
      if (mpValues == null) {
         return parameterValues;
      } else if (parameterValues != null && this.getQueryString() != null) {
         String[] result = new String[mpValues.length + parameterValues.length];
         System.arraycopy(mpValues, 0, result, 0, mpValues.length);
         System.arraycopy(parameterValues, 0, result, mpValues.length, parameterValues.length);
         return result;
      } else {
         return mpValues;
      }
   }

   public Enumeration<String> getParameterNames() {
      Map<String, String[]> multipartParameters = this.getMultipartParameters();
      if (multipartParameters.isEmpty()) {
         return super.getParameterNames();
      }

      Set<String> paramNames = new LinkedHashSet<>();
      paramNames.addAll(Collections.list(super.getParameterNames()));
      paramNames.addAll(multipartParameters.keySet());
      return Collections.enumeration(paramNames);
   }

   public Map<String, String[]> getParameterMap() {
      Map<String, String[]> result = new LinkedHashMap<>();
      Enumeration<String> names = this.getParameterNames();

      while (names.hasMoreElements()) {
         String name = names.nextElement();
         result.put(name, this.getParameterValues(name));
      }

      return result;
   }

   @Override
   public @Nullable String getMultipartContentType(String paramOrFileName) {
      MultipartFile file = this.getFile(paramOrFileName);
      return file != null ? file.getContentType() : this.getMultipartParameterContentTypes().get(paramOrFileName);
   }

   @Override
   public @Nullable HttpHeaders getMultipartHeaders(String paramOrFileName) {
      String contentType = this.getMultipartContentType(paramOrFileName);
      if (contentType != null) {
         HttpHeaders headers = new HttpHeaders();
         headers.add("Content-Type", contentType);
         return headers;
      } else {
         return null;
      }
   }

   protected final void setMultipartParameters(Map<String, String[]> multipartParameters) {
      this.multipartParameters = multipartParameters;
   }

   protected Map<String, String[]> getMultipartParameters() {
      if (this.multipartParameters == null) {
         this.initializeMultipart();
      }

      return this.multipartParameters != null ? this.multipartParameters : Collections.emptyMap();
   }

   protected final void setMultipartParameterContentTypes(Map<String, String> multipartParameterContentTypes) {
      this.multipartParameterContentTypes = multipartParameterContentTypes;
   }

   protected Map<String, String> getMultipartParameterContentTypes() {
      if (this.multipartParameterContentTypes == null) {
         this.initializeMultipart();
      }

      return this.multipartParameterContentTypes != null ? this.multipartParameterContentTypes : Collections.emptyMap();
   }
}
