package org.springframework.web.context.request;

import java.security.Principal;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public interface WebRequest extends RequestAttributes {
   @Nullable String getHeader(String headerName);

   String @Nullable [] getHeaderValues(String headerName);

   Iterator<String> getHeaderNames();

   @Nullable String getParameter(String paramName);

   String @Nullable [] getParameterValues(String paramName);

   Iterator<String> getParameterNames();

   Map<String, String[]> getParameterMap();

   Locale getLocale();

   String getContextPath();

   @Nullable String getRemoteUser();

   @Nullable Principal getUserPrincipal();

   boolean isUserInRole(String role);

   boolean isSecure();

   boolean checkNotModified(long lastModifiedTimestamp);

   boolean checkNotModified(String etag);

   boolean checkNotModified(@Nullable String etag, long lastModifiedTimestamp);

   String getDescription(boolean includeClientInfo);
}
