package org.springframework.web.util;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.MappingMatch;
import java.net.URLDecoder;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

public class UrlPathHelper {
   public static final String PATH_ATTRIBUTE = UrlPathHelper.class.getName() + ".PATH";
   private static final String WEBSPHERE_URI_ATTRIBUTE = "com.ibm.websphere.servlet.uri_non_decoded";
   private static final Log logger = LogFactory.getLog(UrlPathHelper.class);
   static volatile @Nullable Boolean websphereComplianceFlag;
   private boolean alwaysUseFullPath = false;
   private boolean urlDecode = true;
   private boolean removeSemicolonContent = true;
   private String defaultEncoding = "ISO-8859-1";
   private boolean readOnly = false;
   public static final UrlPathHelper defaultInstance = new UrlPathHelper();
   public static final UrlPathHelper rawPathInstance = new UrlPathHelper() {
      @Override
      public String removeSemicolonContent(String requestUri) {
         return requestUri;
      }
   };

   public void setAlwaysUseFullPath(boolean alwaysUseFullPath) {
      this.checkReadOnly();
      this.alwaysUseFullPath = alwaysUseFullPath;
   }

   public void setUrlDecode(boolean urlDecode) {
      this.checkReadOnly();
      this.urlDecode = urlDecode;
   }

   public boolean isUrlDecode() {
      return this.urlDecode;
   }

   public void setRemoveSemicolonContent(boolean removeSemicolonContent) {
      this.checkReadOnly();
      this.removeSemicolonContent = removeSemicolonContent;
   }

   public boolean shouldRemoveSemicolonContent() {
      return this.removeSemicolonContent;
   }

   public void setDefaultEncoding(String defaultEncoding) {
      this.checkReadOnly();
      this.defaultEncoding = defaultEncoding;
   }

   protected String getDefaultEncoding() {
      return this.defaultEncoding;
   }

   private void setReadOnly() {
      this.readOnly = true;
   }

   private void checkReadOnly() {
      Assert.isTrue(!this.readOnly, "This instance cannot be modified");
   }

   public String resolveAndCacheLookupPath(HttpServletRequest request) {
      String lookupPath = this.getLookupPathForRequest(request);
      request.setAttribute(PATH_ATTRIBUTE, lookupPath);
      return lookupPath;
   }

   public static String getResolvedLookupPath(ServletRequest request) {
      String lookupPath = (String)request.getAttribute(PATH_ATTRIBUTE);
      Assert.notNull(lookupPath, () -> "Expected lookupPath in request attribute \"" + PATH_ATTRIBUTE + "\".");
      return lookupPath;
   }

   public String getLookupPathForRequest(HttpServletRequest request) {
      String pathWithinApp = this.getPathWithinApplication(request);
      if (!this.alwaysUseFullPath && !this.ignoreServletPath(request)) {
         String rest = this.getPathWithinServletMapping(request, pathWithinApp);
         return StringUtils.hasLength(rest) ? rest : pathWithinApp;
      } else {
         return pathWithinApp;
      }
   }

   private boolean ignoreServletPath(HttpServletRequest request) {
      HttpServletMapping mapping = (HttpServletMapping)request.getAttribute("jakarta.servlet.include.mapping");
      mapping = mapping == null ? request.getHttpServletMapping() : mapping;
      MappingMatch match = mapping.getMappingMatch();
      return match != null && (!match.equals(MappingMatch.PATH) || mapping.getPattern().equals("/*"));
   }

   public String getPathWithinServletMapping(HttpServletRequest request) {
      return this.getPathWithinServletMapping(request, this.getPathWithinApplication(request));
   }

   protected String getPathWithinServletMapping(HttpServletRequest request, String pathWithinApp) {
      String servletPath = this.getServletPath(request);
      String sanitizedPathWithinApp = getSanitizedPath(pathWithinApp);
      String path;
      if (servletPath.contains(sanitizedPathWithinApp)) {
         path = this.getRemainingPath(sanitizedPathWithinApp, servletPath, false);
      } else {
         path = this.getRemainingPath(pathWithinApp, servletPath, false);
      }

      if (path != null) {
         return path;
      }

      String pathInfo = request.getPathInfo();
      if (pathInfo != null) {
         return pathInfo;
      }

      if (!this.urlDecode) {
         path = this.getRemainingPath(this.decodeInternal(request, pathWithinApp), servletPath, false);
         if (path != null) {
            return pathWithinApp;
         }
      }

      return servletPath;
   }

   public String getPathWithinApplication(HttpServletRequest request) {
      String contextPath = this.getContextPath(request);
      String requestUri = this.getRequestUri(request);
      String path = this.getRemainingPath(requestUri, contextPath, true);
      if (path != null) {
         return StringUtils.hasText(path) ? path : "/";
      } else {
         return requestUri;
      }
   }

   private @Nullable String getRemainingPath(String requestUri, String mapping, boolean ignoreCase) {
      int index1 = 0;

      int index2;
      for (index2 = 0; index1 < requestUri.length() && index2 < mapping.length(); index2++) {
         char c1 = requestUri.charAt(index1);
         char c2 = mapping.charAt(index2);
         if (c1 == ';') {
            index1 = requestUri.indexOf(47, index1);
            if (index1 == -1) {
               return null;
            }

            c1 = requestUri.charAt(index1);
         }

         if (c1 != c2 && (!ignoreCase || Character.toLowerCase(c1) != Character.toLowerCase(c2))) {
            return null;
         }

         index1++;
      }

      if (index2 != mapping.length()) {
         return null;
      }

      if (index1 == requestUri.length()) {
         return "";
      }

      if (requestUri.charAt(index1) == ';') {
         index1 = requestUri.indexOf(47, index1);
      }

      return index1 != -1 ? requestUri.substring(index1) : "";
   }

   private static String getSanitizedPath(final String path) {
      int start = path.indexOf("//");
      if (start == -1) {
         return path;
      }

      char[] content = path.toCharArray();
      int slowIndex = start;

      for (int fastIndex = start + 1; fastIndex < content.length; fastIndex++) {
         if (content[fastIndex] != '/' || content[slowIndex] != '/') {
            content[++slowIndex] = content[fastIndex];
         }
      }

      return new String(content, 0, slowIndex + 1);
   }

   public String getRequestUri(HttpServletRequest request) {
      String uri = (String)request.getAttribute("jakarta.servlet.include.request_uri");
      if (uri == null) {
         uri = request.getRequestURI();
      }

      return this.decodeAndCleanUriString(request, uri);
   }

   public String getContextPath(HttpServletRequest request) {
      String contextPath = (String)request.getAttribute("jakarta.servlet.include.context_path");
      if (contextPath == null) {
         contextPath = request.getContextPath();
      }

      if (StringUtils.matchesCharacter(contextPath, '/')) {
         contextPath = "";
      }

      return this.decodeRequestString(request, contextPath);
   }

   public String getServletPath(HttpServletRequest request) {
      String servletPath = (String)request.getAttribute("jakarta.servlet.include.servlet_path");
      if (servletPath == null) {
         servletPath = request.getServletPath();
      }

      if (servletPath.length() > 1 && servletPath.endsWith("/") && this.shouldRemoveTrailingServletPathSlash(request)) {
         servletPath = servletPath.substring(0, servletPath.length() - 1);
      }

      return servletPath;
   }

   public String getOriginatingRequestUri(HttpServletRequest request) {
      String uri = (String)request.getAttribute("com.ibm.websphere.servlet.uri_non_decoded");
      if (uri == null) {
         uri = (String)request.getAttribute("jakarta.servlet.forward.request_uri");
         if (uri == null) {
            uri = request.getRequestURI();
         }
      }

      return this.decodeAndCleanUriString(request, uri);
   }

   public String getOriginatingContextPath(HttpServletRequest request) {
      String contextPath = (String)request.getAttribute("jakarta.servlet.forward.context_path");
      if (contextPath == null) {
         contextPath = request.getContextPath();
      }

      return this.decodeRequestString(request, contextPath);
   }

   public String getOriginatingServletPath(HttpServletRequest request) {
      String servletPath = (String)request.getAttribute("jakarta.servlet.forward.servlet_path");
      if (servletPath == null) {
         servletPath = request.getServletPath();
      }

      return servletPath;
   }

   public String getOriginatingQueryString(HttpServletRequest request) {
      return request.getAttribute("jakarta.servlet.forward.request_uri") == null && request.getAttribute("jakarta.servlet.error.request_uri") == null
         ? request.getQueryString()
         : (String)request.getAttribute("jakarta.servlet.forward.query_string");
   }

   private String decodeAndCleanUriString(HttpServletRequest request, String uri) {
      uri = this.removeSemicolonContent(uri);
      uri = this.decodeRequestString(request, uri);
      return getSanitizedPath(uri);
   }

   public String decodeRequestString(HttpServletRequest request, String source) {
      return this.urlDecode ? this.decodeInternal(request, source) : source;
   }

   private String decodeInternal(HttpServletRequest request, String source) {
      String enc = this.determineEncoding(request);

      try {
         return UriUtils.decode(source, enc);
      } catch (UnsupportedCharsetException ex) {
         if (logger.isDebugEnabled()) {
            logger.debug(
               "Could not decode request string ["
                  + source
                  + "] with encoding '"
                  + enc
                  + "': falling back to platform default encoding; exception message: "
                  + ex.getMessage()
            );
         }

         return URLDecoder.decode(source);
      }
   }

   protected String determineEncoding(HttpServletRequest request) {
      String enc = request.getCharacterEncoding();
      if (enc == null) {
         enc = this.getDefaultEncoding();
      }

      return enc;
   }

   public String removeSemicolonContent(String requestUri) {
      return this.removeSemicolonContent ? removeSemicolonContentInternal(requestUri) : this.removeJsessionid(requestUri);
   }

   private static String removeSemicolonContentInternal(String requestUri) {
      int semicolonIndex = requestUri.indexOf(59);
      if (semicolonIndex == -1) {
         return requestUri;
      }

      StringBuilder sb;
      for (sb = new StringBuilder(requestUri); semicolonIndex != -1; semicolonIndex = sb.indexOf(";", semicolonIndex)) {
         int slashIndex = sb.indexOf("/", semicolonIndex + 1);
         if (slashIndex == -1) {
            return sb.substring(0, semicolonIndex);
         }

         sb.delete(semicolonIndex, slashIndex);
      }

      return sb.toString();
   }

   private String removeJsessionid(String requestUri) {
      String key = ";jsessionid=";
      int index = requestUri.toLowerCase(Locale.ROOT).indexOf(key);
      if (index == -1) {
         return requestUri;
      }

      String start = requestUri.substring(0, index);

      for (int i = index + key.length(); i < requestUri.length(); i++) {
         char c = requestUri.charAt(i);
         if (c == ';' || c == '/') {
            return start + requestUri.substring(i);
         }
      }

      return start;
   }

   public Map<String, String> decodePathVariables(HttpServletRequest request, Map<String, String> vars) {
      if (this.urlDecode) {
         return vars;
      }

      Map<String, String> decodedVars = CollectionUtils.newLinkedHashMap(vars.size());
      vars.forEach((key, value) -> decodedVars.put(key, this.decodeInternal(request, value)));
      return decodedVars;
   }

   public MultiValueMap<String, String> decodeMatrixVariables(HttpServletRequest request, MultiValueMap<String, String> vars) {
      if (this.urlDecode) {
         return vars;
      }

      MultiValueMap<String, String> decodedVars = new LinkedMultiValueMap(vars.size());
      vars.forEach((key, values) -> {
         for (String value : values) {
            decodedVars.add(key, this.decodeInternal(request, value));
         }
      });
      return decodedVars;
   }

   private boolean shouldRemoveTrailingServletPathSlash(HttpServletRequest request) {
      if (request.getAttribute("com.ibm.websphere.servlet.uri_non_decoded") == null) {
         return false;
      }

      Boolean flagToUse = websphereComplianceFlag;
      if (flagToUse == null) {
         ClassLoader classLoader = UrlPathHelper.class.getClassLoader();
         String className = "com.ibm.ws.webcontainer.WebContainer";
         String methodName = "getWebContainerProperties";
         String propName = "com.ibm.ws.webcontainer.removetrailingservletpathslash";
         boolean flag = false;

         try {
            Class<?> cl = classLoader.loadClass(className);
            Properties prop = (Properties)cl.getMethod(methodName).invoke(null);
            flag = Boolean.parseBoolean(prop.getProperty(propName));
         } catch (Throwable ex) {
            if (logger.isDebugEnabled()) {
               logger.debug("Could not introspect WebSphere web container properties: " + ex);
            }
         }

         flagToUse = flag;
         websphereComplianceFlag = flag;
      }

      return !flagToUse;
   }

   static {
      defaultInstance.setReadOnly();
      rawPathInstance.setAlwaysUseFullPath(true);
      rawPathInstance.setUrlDecode(false);
      rawPathInstance.setRemoveSemicolonContent(false);
      rawPathInstance.setReadOnly();
   }
}
