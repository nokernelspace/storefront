package org.springframework.web.util;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.ServletResponseWrapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public abstract class WebUtils {
   public static final String INCLUDE_REQUEST_URI_ATTRIBUTE = "jakarta.servlet.include.request_uri";
   public static final String INCLUDE_CONTEXT_PATH_ATTRIBUTE = "jakarta.servlet.include.context_path";
   public static final String INCLUDE_SERVLET_PATH_ATTRIBUTE = "jakarta.servlet.include.servlet_path";
   public static final String INCLUDE_PATH_INFO_ATTRIBUTE = "jakarta.servlet.include.path_info";
   public static final String INCLUDE_QUERY_STRING_ATTRIBUTE = "jakarta.servlet.include.query_string";
   public static final String FORWARD_REQUEST_URI_ATTRIBUTE = "jakarta.servlet.forward.request_uri";
   public static final String FORWARD_CONTEXT_PATH_ATTRIBUTE = "jakarta.servlet.forward.context_path";
   public static final String FORWARD_SERVLET_PATH_ATTRIBUTE = "jakarta.servlet.forward.servlet_path";
   public static final String FORWARD_PATH_INFO_ATTRIBUTE = "jakarta.servlet.forward.path_info";
   public static final String FORWARD_QUERY_STRING_ATTRIBUTE = "jakarta.servlet.forward.query_string";
   public static final String ERROR_STATUS_CODE_ATTRIBUTE = "jakarta.servlet.error.status_code";
   public static final String ERROR_EXCEPTION_TYPE_ATTRIBUTE = "jakarta.servlet.error.exception_type";
   public static final String ERROR_MESSAGE_ATTRIBUTE = "jakarta.servlet.error.message";
   public static final String ERROR_EXCEPTION_ATTRIBUTE = "jakarta.servlet.error.exception";
   public static final String ERROR_REQUEST_URI_ATTRIBUTE = "jakarta.servlet.error.request_uri";
   public static final String ERROR_SERVLET_NAME_ATTRIBUTE = "jakarta.servlet.error.servlet_name";
   public static final String CONTENT_TYPE_CHARSET_PREFIX = ";charset=";
   public static final String DEFAULT_CHARACTER_ENCODING = "ISO-8859-1";
   public static final String TEMP_DIR_CONTEXT_ATTRIBUTE = "jakarta.servlet.context.tempdir";
   public static final String HTML_ESCAPE_CONTEXT_PARAM = "defaultHtmlEscape";
   public static final String RESPONSE_ENCODED_HTML_ESCAPE_CONTEXT_PARAM = "responseEncodedHtmlEscape";
   public static final String WEB_APP_ROOT_KEY_PARAM = "webAppRootKey";
   public static final String DEFAULT_WEB_APP_ROOT_KEY = "webapp.root";
   public static final String[] SUBMIT_IMAGE_SUFFIXES = new String[]{".x", ".y"};
   public static final String SESSION_MUTEX_ATTRIBUTE = WebUtils.class.getName() + ".MUTEX";

   public static void setWebAppRootSystemProperty(ServletContext servletContext) throws IllegalStateException {
      Assert.notNull(servletContext, "ServletContext must not be null");
      String root = servletContext.getRealPath("/");
      if (root == null) {
         throw new IllegalStateException("Cannot set web app root system property when WAR file is not expanded");
      }

      String param = servletContext.getInitParameter("webAppRootKey");
      String key = param != null ? param : "webapp.root";
      String oldValue = System.getProperty(key);
      if (oldValue != null && !StringUtils.pathEquals(oldValue, root)) {
         throw new IllegalStateException(
            "Web app root system property already set to different value: '"
               + key
               + "' = ["
               + oldValue
               + "] instead of ["
               + root
               + "] - Choose unique values for the 'webAppRootKey' context-param in your web.xml files!"
         );
      }

      System.setProperty(key, root);
      servletContext.log("Set web app root system property: '" + key + "' = [" + root + "]");
   }

   public static void removeWebAppRootSystemProperty(ServletContext servletContext) {
      Assert.notNull(servletContext, "ServletContext must not be null");
      String param = servletContext.getInitParameter("webAppRootKey");
      String key = param != null ? param : "webapp.root";
      System.clearProperty(key);
   }

   public static @Nullable Boolean getDefaultHtmlEscape(@Nullable ServletContext servletContext) {
      if (servletContext == null) {
         return null;
      }

      String param = servletContext.getInitParameter("defaultHtmlEscape");
      return StringUtils.hasText(param) ? Boolean.valueOf(param) : null;
   }

   public static @Nullable Boolean getResponseEncodedHtmlEscape(@Nullable ServletContext servletContext) {
      if (servletContext == null) {
         return null;
      }

      String param = servletContext.getInitParameter("responseEncodedHtmlEscape");
      return StringUtils.hasText(param) ? Boolean.valueOf(param) : null;
   }

   public static File getTempDir(ServletContext servletContext) {
      Assert.notNull(servletContext, "ServletContext must not be null");
      return (File)servletContext.getAttribute("jakarta.servlet.context.tempdir");
   }

   public static String getRealPath(ServletContext servletContext, String path) throws FileNotFoundException {
      Assert.notNull(servletContext, "ServletContext must not be null");
      if (!path.startsWith("/")) {
         path = "/" + path;
      }

      String realPath = servletContext.getRealPath(path);
      if (realPath == null) {
         throw new FileNotFoundException(
            "ServletContext resource [" + path + "] cannot be resolved to absolute file path - web application archive not expanded?"
         );
      } else {
         return realPath;
      }
   }

   public static @Nullable String getSessionId(HttpServletRequest request) {
      Assert.notNull(request, "Request must not be null");
      HttpSession session = request.getSession(false);
      return session != null ? session.getId() : null;
   }

   public static @Nullable Object getSessionAttribute(HttpServletRequest request, String name) {
      Assert.notNull(request, "Request must not be null");
      HttpSession session = request.getSession(false);
      return session != null ? session.getAttribute(name) : null;
   }

   public static Object getRequiredSessionAttribute(HttpServletRequest request, String name) throws IllegalStateException {
      Object attr = getSessionAttribute(request, name);
      if (attr == null) {
         throw new IllegalStateException("No session attribute '" + name + "' found");
      } else {
         return attr;
      }
   }

   public static void setSessionAttribute(HttpServletRequest request, String name, @Nullable Object value) {
      Assert.notNull(request, "Request must not be null");
      if (value != null) {
         request.getSession().setAttribute(name, value);
      } else {
         HttpSession session = request.getSession(false);
         if (session != null) {
            session.removeAttribute(name);
         }
      }
   }

   public static Object getSessionMutex(HttpSession session) {
      Assert.notNull(session, "Session must not be null");
      Object mutex = session.getAttribute(SESSION_MUTEX_ATTRIBUTE);
      if (mutex == null) {
         mutex = session;
      }

      return mutex;
   }

   public static <T> @Nullable T getNativeRequest(ServletRequest request, @Nullable Class<T> requiredType) {
      if (requiredType != null) {
         if (requiredType.isInstance(request)) {
            return (T)request;
         }

         if (request instanceof ServletRequestWrapper wrapper) {
            return getNativeRequest(wrapper.getRequest(), requiredType);
         }
      }

      return null;
   }

   public static <T> @Nullable T getNativeResponse(ServletResponse response, @Nullable Class<T> requiredType) {
      if (requiredType != null) {
         if (requiredType.isInstance(response)) {
            return (T)response;
         }

         if (response instanceof ServletResponseWrapper wrapper) {
            return getNativeResponse(wrapper.getResponse(), requiredType);
         }
      }

      return null;
   }

   public static boolean isIncludeRequest(ServletRequest request) {
      return request.getAttribute("jakarta.servlet.include.request_uri") != null;
   }

   public static void exposeErrorRequestAttributes(HttpServletRequest request, Throwable ex, @Nullable String servletName) {
      exposeRequestAttributeIfNotPresent(request, "jakarta.servlet.error.status_code", 200);
      exposeRequestAttributeIfNotPresent(request, "jakarta.servlet.error.exception_type", ex.getClass());
      exposeRequestAttributeIfNotPresent(request, "jakarta.servlet.error.message", ex.getMessage());
      exposeRequestAttributeIfNotPresent(request, "jakarta.servlet.error.exception", ex);
      exposeRequestAttributeIfNotPresent(request, "jakarta.servlet.error.request_uri", request.getRequestURI());
      if (servletName != null) {
         exposeRequestAttributeIfNotPresent(request, "jakarta.servlet.error.servlet_name", servletName);
      }
   }

   private static void exposeRequestAttributeIfNotPresent(ServletRequest request, String name, @Nullable Object value) {
      if (request.getAttribute(name) == null) {
         request.setAttribute(name, value);
      }
   }

   public static void clearErrorRequestAttributes(HttpServletRequest request) {
      request.removeAttribute("jakarta.servlet.error.status_code");
      request.removeAttribute("jakarta.servlet.error.exception_type");
      request.removeAttribute("jakarta.servlet.error.message");
      request.removeAttribute("jakarta.servlet.error.exception");
      request.removeAttribute("jakarta.servlet.error.request_uri");
      request.removeAttribute("jakarta.servlet.error.servlet_name");
   }

   public static @Nullable Cookie getCookie(HttpServletRequest request, String name) {
      Assert.notNull(request, "Request must not be null");
      Cookie[] cookies = request.getCookies();
      if (cookies != null) {
         for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
               return cookie;
            }
         }
      }

      return null;
   }

   public static boolean hasSubmitParameter(ServletRequest request, String name) {
      Assert.notNull(request, "Request must not be null");
      if (request.getParameter(name) != null) {
         return true;
      }

      for (String suffix : SUBMIT_IMAGE_SUFFIXES) {
         if (request.getParameter(name + suffix) != null) {
            return true;
         }
      }

      return false;
   }

   public static @Nullable String findParameterValue(ServletRequest request, String name) {
      return findParameterValue(request.getParameterMap(), name);
   }

   public static @Nullable String findParameterValue(Map<String, ?> parameters, String name) {
      Object value = parameters.get(name);
      if (value instanceof String[] values) {
         return values.length > 0 ? values[0] : null;
      } else {
         if (value != null) {
            return value.toString();
         }

         String prefix = name + "_";

         for (String paramName : parameters.keySet()) {
            if (paramName.startsWith(prefix)) {
               for (String suffix : SUBMIT_IMAGE_SUFFIXES) {
                  if (paramName.endsWith(suffix)) {
                     return paramName.substring(prefix.length(), paramName.length() - suffix.length());
                  }
               }

               return paramName.substring(prefix.length());
            }
         }

         return null;
      }
   }

   public static Map<String, Object> getParametersStartingWith(ServletRequest request, @Nullable String prefix) {
      Assert.notNull(request, "Request must not be null");
      Enumeration<String> paramNames = request.getParameterNames();
      Map<String, Object> params = new TreeMap<>();
      if (prefix == null) {
         prefix = "";
      }

      while (paramNames != null && paramNames.hasMoreElements()) {
         String paramName = paramNames.nextElement();
         if (prefix.isEmpty() || paramName.startsWith(prefix)) {
            String unprefixed = paramName.substring(prefix.length());
            String[] values = request.getParameterValues(paramName);
            if (values != null && values.length != 0) {
               if (values.length > 1) {
                  params.put(unprefixed, values);
               } else {
                  params.put(unprefixed, values[0]);
               }
            }
         }
      }

      return params;
   }

   public static MultiValueMap<String, String> parseMatrixVariables(String matrixVariables) {
      MultiValueMap<String, String> result = new LinkedMultiValueMap();
      if (!StringUtils.hasText(matrixVariables)) {
         return result;
      }

      StringTokenizer pairs = new StringTokenizer(matrixVariables, ";");

      while (pairs.hasMoreTokens()) {
         String pair = pairs.nextToken();
         int index = pair.indexOf(61);
         if (index != -1) {
            String name = pair.substring(0, index);
            if (!name.equalsIgnoreCase("jsessionid")) {
               String rawValue = pair.substring(index + 1);

               for (String value : StringUtils.commaDelimitedListToStringArray(rawValue)) {
                  result.add(name, value);
               }
            }
         } else {
            result.add(pair, "");
         }
      }

      return result;
   }

   public static boolean isValidOrigin(HttpRequest request, Collection<String> allowedOrigins) {
      Assert.notNull(request, "Request must not be null");
      Assert.notNull(allowedOrigins, "Allowed origins must not be null");
      String origin = request.getHeaders().getOrigin();
      if (origin == null || allowedOrigins.contains("*")) {
         return true;
      } else {
         return CollectionUtils.isEmpty(allowedOrigins) ? isSameOrigin(request) : allowedOrigins.contains(origin);
      }
   }

   public static boolean isSameOrigin(HttpRequest request) {
      HttpHeaders headers = request.getHeaders();
      String origin = headers.getOrigin();
      if (origin == null) {
         return true;
      }

      String scheme;
      String host;
      int port;
      if (request instanceof ServletServerHttpRequest servletServerHttpRequest) {
         HttpServletRequest servletRequest = servletServerHttpRequest.getServletRequest();
         scheme = servletRequest.getScheme();
         host = servletRequest.getServerName();
         port = servletRequest.getServerPort();
      } else {
         URI uri = request.getURI();
         scheme = uri.getScheme();
         host = uri.getHost();
         port = uri.getPort();
      }

      UriComponents originUrl = UriComponentsBuilder.fromUriString(origin).build();
      return ObjectUtils.nullSafeEquals(scheme, originUrl.getScheme())
         && ObjectUtils.nullSafeEquals(host, originUrl.getHost())
         && getPort(scheme, port) == getPort(originUrl.getScheme(), originUrl.getPort());
   }

   private static int getPort(@Nullable String scheme, int port) {
      if (port == -1) {
         if ("http".equals(scheme) || "ws".equals(scheme)) {
            port = 80;
         } else if ("https".equals(scheme) || "wss".equals(scheme)) {
            port = 443;
         }
      }

      return port;
   }
}
