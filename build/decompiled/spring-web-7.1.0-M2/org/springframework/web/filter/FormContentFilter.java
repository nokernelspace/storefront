package org.springframework.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

public class FormContentFilter extends OncePerRequestFilter {
   private static final List<String> HTTP_METHODS = Arrays.asList("PUT", "PATCH", "DELETE", "QUERY");
   private FormHttpMessageConverter formConverter = new FormHttpMessageConverter();

   public void setFormConverter(FormHttpMessageConverter converter) {
      Assert.notNull(converter, "FormHttpMessageConverter is required");
      this.formConverter = converter;
   }

   public void setCharset(Charset charset) {
      this.formConverter.setCharset(charset);
   }

   @Override
   protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
      MultiValueMap<String, String> params = this.parseIfNecessary(request);
      if (!CollectionUtils.isEmpty(params)) {
         filterChain.doFilter(new FormContentFilter.FormContentRequestWrapper(request, params), response);
      } else {
         filterChain.doFilter(request, response);
      }
   }

   private @Nullable MultiValueMap<String, String> parseIfNecessary(HttpServletRequest request) throws IOException {
      if (!this.shouldParse(request)) {
         return null;
      }

      HttpInputMessage inputMessage = new ServletServerHttpRequest(request) {
         @Override
         public InputStream getBody() throws IOException {
            return request.getInputStream();
         }
      };
      return (MultiValueMap<String, String>)this.formConverter
         .read(ResolvableType.forClassWithGenerics(MultiValueMap.class, new Class[]{String.class, String.class}), inputMessage, null);
   }

   private boolean shouldParse(HttpServletRequest request) {
      String contentType = request.getContentType();
      String method = request.getMethod();
      if (StringUtils.hasLength(contentType) && HTTP_METHODS.contains(method)) {
         try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            return MediaType.APPLICATION_FORM_URLENCODED.includes(mediaType);
         } catch (IllegalArgumentException var5) {
         }
      }

      return false;
   }

   private static class FormContentRequestWrapper extends HttpServletRequestWrapper {
      private final MultiValueMap<String, String> formParams;

      public FormContentRequestWrapper(HttpServletRequest request, MultiValueMap<String, String> params) {
         super(request);
         this.formParams = params;
      }

      public @Nullable String getParameter(String name) {
         String queryStringValue = super.getParameter(name);
         String formValue = (String)this.formParams.getFirst(name);
         return queryStringValue != null ? queryStringValue : formValue;
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

      public Enumeration<String> getParameterNames() {
         Set<String> names = new LinkedHashSet<>();
         names.addAll(Collections.list(super.getParameterNames()));
         names.addAll(this.formParams.keySet());
         return Collections.enumeration(names);
      }

      public String[] getParameterValues(String name) {
         String[] parameterValues = super.getParameterValues(name);
         List<String> formParam = (List<String>)this.formParams.get(name);
         if (formParam == null) {
            return parameterValues;
         } else if (parameterValues != null && this.getQueryString() != null) {
            List<String> result = new ArrayList<>(parameterValues.length + formParam.size());
            result.addAll(Arrays.asList(parameterValues));
            result.addAll(formParam);
            return StringUtils.toStringArray(result);
         } else {
            return StringUtils.toStringArray(formParam);
         }
      }
   }
}
