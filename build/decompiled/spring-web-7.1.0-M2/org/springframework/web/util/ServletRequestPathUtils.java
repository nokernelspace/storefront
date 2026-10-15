package org.springframework.web.util;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.MappingMatch;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public abstract class ServletRequestPathUtils {
   public static final String PATH_ATTRIBUTE = ServletRequestPathUtils.class.getName() + ".PATH";

   public static RequestPath parse(HttpServletRequest request) {
      return ServletRequestPathUtils.ServletRequestPath.parse(request);
   }

   public static RequestPath parseAndCache(HttpServletRequest request) {
      RequestPath requestPath = ServletRequestPathUtils.ServletRequestPath.parse(request);
      request.setAttribute(PATH_ATTRIBUTE, requestPath);
      return requestPath;
   }

   public static RequestPath getParsedRequestPath(ServletRequest request) {
      RequestPath path = (RequestPath)request.getAttribute(PATH_ATTRIBUTE);
      Assert.notNull(path, () -> "Expected parsed RequestPath in request attribute \"" + PATH_ATTRIBUTE + "\".");
      return path;
   }

   public static void setParsedRequestPath(@Nullable RequestPath requestPath, ServletRequest request) {
      if (requestPath != null) {
         request.setAttribute(PATH_ATTRIBUTE, requestPath);
      } else {
         request.removeAttribute(PATH_ATTRIBUTE);
      }
   }

   public static boolean hasParsedRequestPath(ServletRequest request) {
      return request.getAttribute(PATH_ATTRIBUTE) != null;
   }

   public static void clearParsedRequestPath(ServletRequest request) {
      request.removeAttribute(PATH_ATTRIBUTE);
   }

   public static Object getCachedPath(ServletRequest request) {
      String lookupPath = (String)request.getAttribute(UrlPathHelper.PATH_ATTRIBUTE);
      if (lookupPath != null) {
         return lookupPath;
      } else {
         RequestPath requestPath = (RequestPath)request.getAttribute(PATH_ATTRIBUTE);
         if (requestPath != null) {
            return requestPath.pathWithinApplication();
         } else {
            throw new IllegalArgumentException("Neither a pre-parsed RequestPath nor a pre-resolved String lookupPath is available.");
         }
      }
   }

   public static String getCachedPathValue(ServletRequest request) {
      Object path = getCachedPath(request);
      if (path instanceof PathContainer pathContainer) {
         String value = pathContainer.value();
         path = UrlPathHelper.defaultInstance.removeSemicolonContent(value);
      }

      return (String)path;
   }

   public static boolean hasCachedPath(ServletRequest request) {
      return request.getAttribute(PATH_ATTRIBUTE) != null || request.getAttribute(UrlPathHelper.PATH_ATTRIBUTE) != null;
   }

   public static @Nullable String getServletPathPrefix(HttpServletRequest request) {
      HttpServletMapping mapping = (HttpServletMapping)request.getAttribute("jakarta.servlet.include.mapping");
      mapping = mapping != null ? mapping : request.getHttpServletMapping();
      if (ObjectUtils.nullSafeEquals(mapping.getMappingMatch(), MappingMatch.PATH)) {
         String servletPath = (String)request.getAttribute("jakarta.servlet.include.servlet_path");
         servletPath = servletPath != null ? servletPath : request.getServletPath();
         return servletPath.endsWith("/") ? servletPath.substring(0, servletPath.length() - 1) : servletPath;
      } else {
         return null;
      }
   }

   private static final class ServletRequestPath implements RequestPath {
      private final ServletRequestPathUtils.ServletRequestPath.PathElements pathElements;
      private final RequestPath requestPath;
      private final PathContainer contextPath;

      private ServletRequestPath(ServletRequestPathUtils.ServletRequestPath.PathElements pathElements) {
         this.pathElements = pathElements;
         this.requestPath = pathElements.createRequestPath();
         this.contextPath = pathElements.createContextPath();
      }

      @Override
      public String value() {
         return this.requestPath.value();
      }

      @Override
      public List<PathContainer.Element> elements() {
         return this.requestPath.elements();
      }

      @Override
      public PathContainer contextPath() {
         return this.contextPath;
      }

      @Override
      public PathContainer pathWithinApplication() {
         return this.requestPath.pathWithinApplication();
      }

      @Override
      public RequestPath modifyContextPath(String contextPath) {
         return new ServletRequestPathUtils.ServletRequestPath(this.pathElements.withContextPath(contextPath));
      }

      @Override
      public boolean equals(@Nullable Object other) {
         if (this == other) {
            return true;
         } else {
            return other != null && this.getClass() == other.getClass()
               ? this.requestPath.equals(((ServletRequestPathUtils.ServletRequestPath)other).requestPath)
               : false;
         }
      }

      @Override
      public int hashCode() {
         return this.requestPath.hashCode();
      }

      @Override
      public String toString() {
         return this.requestPath.toString();
      }

      public static RequestPath parse(HttpServletRequest request) {
         String requestUri = (String)request.getAttribute("jakarta.servlet.include.request_uri");
         requestUri = requestUri != null ? requestUri : request.getRequestURI();
         String servletPathPrefix = ServletRequestPathUtils.getServletPathPrefix(request);
         if (!StringUtils.hasLength(servletPathPrefix)) {
            return RequestPath.parse(requestUri, request.getContextPath());
         }

         servletPathPrefix = UriUtils.encodePath(servletPathPrefix, StandardCharsets.UTF_8);
         return new ServletRequestPathUtils.ServletRequestPath(
            new ServletRequestPathUtils.ServletRequestPath.PathElements(requestUri, request.getContextPath(), servletPathPrefix)
         );
      }

      record PathElements(String rawPath, @Nullable String contextPath, String servletPathPrefix) {
         PathElements {
            Assert.notNull(servletPathPrefix, "`servletPathPrefix` is required");
         }

         private RequestPath createRequestPath() {
            return RequestPath.parse(this.rawPath, this.contextPath + this.servletPathPrefix);
         }

         private PathContainer createContextPath() {
            return PathContainer.parsePath(StringUtils.hasText(this.contextPath) ? this.contextPath : "");
         }

         ServletRequestPathUtils.ServletRequestPath.PathElements withContextPath(String contextPath) {
            if (contextPath.startsWith("/") && !contextPath.endsWith("/")) {
               String contextPathToUse = this.servletPathPrefix + contextPath;
               if (StringUtils.hasText(this.contextPath())) {
                  throw new IllegalStateException("Could not change context path to '" + contextPathToUse + "': a context path is already specified");
               } else if (!this.rawPath.startsWith(contextPathToUse)) {
                  throw new IllegalArgumentException(
                     "Invalid contextPath '" + contextPathToUse + "': must match the start of requestPath: '" + this.rawPath + "'"
                  );
               } else {
                  return new ServletRequestPathUtils.ServletRequestPath.PathElements(this.rawPath, contextPathToUse, "");
               }
            } else {
               throw new IllegalArgumentException("Invalid contextPath '" + contextPath + "': must start with '/' and not end with '/'");
            }
         }
      }
   }
}
