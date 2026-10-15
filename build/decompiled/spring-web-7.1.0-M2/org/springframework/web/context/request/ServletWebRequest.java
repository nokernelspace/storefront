package org.springframework.web.context.request;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ETag;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.util.WebUtils;

public class ServletWebRequest extends ServletRequestAttributes implements NativeWebRequest {
   private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD");
   private static final String[] DATE_FORMATS = new String[]{"EEE, dd MMM yyyy HH:mm:ss zzz", "EEE, dd-MMM-yy HH:mm:ss zzz", "EEE MMM dd HH:mm:ss yyyy"};
   private static final TimeZone GMT = TimeZone.getTimeZone("GMT");
   private boolean notModified = false;

   public ServletWebRequest(HttpServletRequest request) {
      super(request);
   }

   public ServletWebRequest(HttpServletRequest request, @Nullable HttpServletResponse response) {
      super(request, response);
   }

   @Override
   public Object getNativeRequest() {
      return this.getRequest();
   }

   @Override
   public @Nullable Object getNativeResponse() {
      return this.getResponse();
   }

   @Override
   public <T> @Nullable T getNativeRequest(@Nullable Class<T> requiredType) {
      return WebUtils.getNativeRequest(this.getRequest(), requiredType);
   }

   @Override
   public <T> @Nullable T getNativeResponse(@Nullable Class<T> requiredType) {
      HttpServletResponse response = this.getResponse();
      return response != null ? WebUtils.getNativeResponse(response, requiredType) : null;
   }

   public HttpMethod getHttpMethod() {
      return HttpMethod.valueOf(this.getRequest().getMethod());
   }

   @Override
   public @Nullable String getHeader(String headerName) {
      return this.getRequest().getHeader(headerName);
   }

   @Override
   public String @Nullable [] getHeaderValues(String headerName) {
      String[] headerValues = StringUtils.toStringArray(this.getRequest().getHeaders(headerName));
      return !ObjectUtils.isEmpty(headerValues) ? headerValues : null;
   }

   @Override
   public Iterator<String> getHeaderNames() {
      return CollectionUtils.toIterator(this.getRequest().getHeaderNames());
   }

   @Override
   public @Nullable String getParameter(String paramName) {
      return this.getRequest().getParameter(paramName);
   }

   @Override
   public String @Nullable [] getParameterValues(String paramName) {
      return this.getRequest().getParameterValues(paramName);
   }

   @Override
   public Iterator<String> getParameterNames() {
      return CollectionUtils.toIterator(this.getRequest().getParameterNames());
   }

   @Override
   public Map<String, String[]> getParameterMap() {
      return this.getRequest().getParameterMap();
   }

   @Override
   public Locale getLocale() {
      return this.getRequest().getLocale();
   }

   @Override
   public String getContextPath() {
      return this.getRequest().getContextPath();
   }

   @Override
   public @Nullable String getRemoteUser() {
      return this.getRequest().getRemoteUser();
   }

   @Override
   public @Nullable Principal getUserPrincipal() {
      return this.getRequest().getUserPrincipal();
   }

   @Override
   public boolean isUserInRole(String role) {
      return this.getRequest().isUserInRole(role);
   }

   @Override
   public boolean isSecure() {
      return this.getRequest().isSecure();
   }

   @Override
   public boolean checkNotModified(long lastModifiedTimestamp) {
      return this.checkNotModified(null, lastModifiedTimestamp);
   }

   @Override
   public boolean checkNotModified(String etag) {
      return this.checkNotModified(etag, -1L);
   }

   @Override
   public boolean checkNotModified(@Nullable String etag, long lastModifiedTimestamp) {
      if (this.notModified) {
         return true;
      }

      HttpServletResponse response = this.getResponse();
      if (response != null && HttpStatus.OK.value() != response.getStatus()) {
         return false;
      }

      if (this.validateIfMatch(etag)) {
         this.updateResponseStateChanging(etag, lastModifiedTimestamp);
         return this.notModified;
      }

      if (this.validateIfUnmodifiedSince(lastModifiedTimestamp)) {
         this.updateResponseStateChanging(etag, lastModifiedTimestamp);
         return this.notModified;
      }

      if (!this.validateIfNoneMatch(etag)) {
         this.validateIfModifiedSince(lastModifiedTimestamp);
      }

      this.updateResponseIdempotent(etag, lastModifiedTimestamp);
      return this.notModified;
   }

   private boolean validateIfMatch(@Nullable String etag) {
      if (SAFE_METHODS.contains(this.getRequest().getMethod())) {
         return false;
      }

      Enumeration<String> ifMatchHeaders = this.getRequest().getHeaders("If-Match");
      if (!ifMatchHeaders.hasMoreElements()) {
         return false;
      }

      this.notModified = this.matchRequestedETags(ifMatchHeaders, etag, false);
      return true;
   }

   private boolean validateIfNoneMatch(@Nullable String etag) {
      Enumeration<String> ifNoneMatchHeaders = this.getRequest().getHeaders("If-None-Match");
      if (!ifNoneMatchHeaders.hasMoreElements()) {
         return false;
      }

      this.notModified = !this.matchRequestedETags(ifNoneMatchHeaders, etag, true);
      return true;
   }

   private boolean matchRequestedETags(Enumeration<String> requestedETags, @Nullable String tag, boolean weakCompare) {
      if (StringUtils.hasLength(tag)) {
         ETag eTag = ETag.create(tag);
         boolean isNotSafeMethod = !SAFE_METHODS.contains(this.getRequest().getMethod());

         while (requestedETags.hasMoreElements()) {
            for (ETag requestedETag : ETag.parse(requestedETags.nextElement())) {
               if (requestedETag.isWildcard() && isNotSafeMethod) {
                  return false;
               }

               if (requestedETag.compare(eTag, !weakCompare)) {
                  return false;
               }
            }
         }
      }

      return true;
   }

   private void updateResponseStateChanging(@Nullable String etag, long lastModifiedTimestamp) {
      if (this.notModified && this.getResponse() != null) {
         this.getResponse().setStatus(HttpStatus.PRECONDITION_FAILED.value());
      } else {
         this.addCachingResponseHeaders(etag, lastModifiedTimestamp);
      }
   }

   private boolean validateIfUnmodifiedSince(long lastModifiedTimestamp) {
      if (lastModifiedTimestamp < 0L) {
         return false;
      }

      long ifUnmodifiedSince = this.parseDateHeader("If-Unmodified-Since");
      if (ifUnmodifiedSince == -1L) {
         return false;
      }

      this.notModified = ifUnmodifiedSince < lastModifiedTimestamp / 1000L * 1000L;
      return true;
   }

   private void validateIfModifiedSince(long lastModifiedTimestamp) {
      if (lastModifiedTimestamp >= 0L) {
         long ifModifiedSince = this.parseDateHeader("If-Modified-Since");
         if (ifModifiedSince != -1L) {
            this.notModified = ifModifiedSince >= lastModifiedTimestamp / 1000L * 1000L;
         }
      }
   }

   private void updateResponseIdempotent(@Nullable String etag, long lastModifiedTimestamp) {
      if (this.getResponse() != null) {
         boolean isHttpGetOrHead = SAFE_METHODS.contains(this.getRequest().getMethod());
         if (this.notModified) {
            this.getResponse().setStatus(isHttpGetOrHead ? HttpStatus.NOT_MODIFIED.value() : HttpStatus.PRECONDITION_FAILED.value());
         }

         this.addCachingResponseHeaders(etag, lastModifiedTimestamp);
      }
   }

   private void addCachingResponseHeaders(@Nullable String eTag, long lastModifiedTimestamp) {
      if (this.getResponse() != null && SAFE_METHODS.contains(this.getRequest().getMethod())) {
         if (lastModifiedTimestamp > 0L && this.parseDateValue(this.getResponse().getHeader("Last-Modified")) == -1L) {
            this.getResponse().setDateHeader("Last-Modified", lastModifiedTimestamp);
         }

         if (StringUtils.hasLength(eTag) && this.getResponse().getHeader("ETag") == null) {
            this.getResponse().setHeader("ETag", ETag.quoteETagIfNecessary(eTag));
         }
      }
   }

   public boolean isNotModified() {
      return this.notModified;
   }

   private long parseDateHeader(String headerName) {
      long dateValue = -1L;

      try {
         dateValue = this.getRequest().getDateHeader(headerName);
      } catch (IllegalArgumentException ex) {
         String headerValue = this.getHeader(headerName);
         if (headerValue != null) {
            int separatorIndex = headerValue.indexOf(59);
            if (separatorIndex != -1) {
               String datePart = headerValue.substring(0, separatorIndex);
               dateValue = this.parseDateValue(datePart);
            }
         }
      }

      return dateValue;
   }

   private long parseDateValue(@Nullable String headerValue) {
      if (headerValue == null) {
         return -1L;
      }

      if (headerValue.length() >= 3) {
         for (String dateFormat : DATE_FORMATS) {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(dateFormat, Locale.US);
            simpleDateFormat.setTimeZone(GMT);

            try {
               return simpleDateFormat.parse(headerValue).getTime();
            } catch (ParseException var8) {
            }
         }
      }

      return -1L;
   }

   @Override
   public String getDescription(boolean includeClientInfo) {
      HttpServletRequest request = this.getRequest();
      StringBuilder sb = new StringBuilder();
      sb.append("uri=").append(request.getRequestURI());
      if (includeClientInfo) {
         String client = request.getRemoteAddr();
         if (StringUtils.hasLength(client)) {
            sb.append(";client=").append(client);
         }

         HttpSession session = request.getSession(false);
         if (session != null) {
            sb.append(";session=").append(session.getId());
         }

         String user = request.getRemoteUser();
         if (StringUtils.hasLength(user)) {
            sb.append(";user=").append(user);
         }
      }

      return sb.toString();
   }

   @Override
   public String toString() {
      return "ServletWebRequest: " + this.getDescription(true);
   }
}
