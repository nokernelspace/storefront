package org.springframework.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.WebUtils;

public abstract class AbstractRequestLoggingFilter extends OncePerRequestFilter {
   public static final String DEFAULT_BEFORE_MESSAGE_PREFIX = "Before request [";
   public static final String DEFAULT_BEFORE_MESSAGE_SUFFIX = "]";
   public static final String DEFAULT_AFTER_MESSAGE_PREFIX = "After request [";
   public static final String DEFAULT_AFTER_MESSAGE_SUFFIX = "]";
   private static final int DEFAULT_MAX_PAYLOAD_LENGTH = 50;
   private boolean includeQueryString = false;
   private boolean includeClientInfo = false;
   private boolean includeHeaders = false;
   private boolean includePayload = false;
   private @Nullable Predicate<String> queryParamPredicate;
   private @Nullable Predicate<String> headerPredicate;
   private int maxPayloadLength = 50;
   private String beforeMessagePrefix = "Before request [";
   private String beforeMessageSuffix = "]";
   private String afterMessagePrefix = "After request [";
   private String afterMessageSuffix = "]";

   public void setIncludeQueryString(boolean includeQueryString) {
      this.includeQueryString = includeQueryString;
   }

   protected boolean isIncludeQueryString() {
      return this.includeQueryString;
   }

   public void setIncludeClientInfo(boolean includeClientInfo) {
      this.includeClientInfo = includeClientInfo;
   }

   protected boolean isIncludeClientInfo() {
      return this.includeClientInfo;
   }

   public void setIncludeHeaders(boolean includeHeaders) {
      this.includeHeaders = includeHeaders;
   }

   protected boolean isIncludeHeaders() {
      return this.includeHeaders;
   }

   public void setIncludePayload(boolean includePayload) {
      this.includePayload = includePayload;
   }

   protected boolean isIncludePayload() {
      return this.includePayload;
   }

   public void setQueryParamPredicate(@Nullable Predicate<String> queryParamPredicate) {
      this.queryParamPredicate = queryParamPredicate;
   }

   protected @Nullable Predicate<String> getQueryParamPredicate() {
      return this.queryParamPredicate;
   }

   public void setHeaderPredicate(@Nullable Predicate<String> headerPredicate) {
      this.headerPredicate = headerPredicate;
   }

   protected @Nullable Predicate<String> getHeaderPredicate() {
      return this.headerPredicate;
   }

   public void setMaxPayloadLength(int maxPayloadLength) {
      Assert.isTrue(maxPayloadLength >= 0, "'maxPayloadLength' must be greater than or equal to 0");
      this.maxPayloadLength = maxPayloadLength;
   }

   protected int getMaxPayloadLength() {
      return this.maxPayloadLength;
   }

   public void setBeforeMessagePrefix(String beforeMessagePrefix) {
      this.beforeMessagePrefix = beforeMessagePrefix;
   }

   public void setBeforeMessageSuffix(String beforeMessageSuffix) {
      this.beforeMessageSuffix = beforeMessageSuffix;
   }

   public void setAfterMessagePrefix(String afterMessagePrefix) {
      this.afterMessagePrefix = afterMessagePrefix;
   }

   public void setAfterMessageSuffix(String afterMessageSuffix) {
      this.afterMessageSuffix = afterMessageSuffix;
   }

   @Override
   protected boolean shouldNotFilterAsyncDispatch() {
      return false;
   }

   @Override
   protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
      boolean isFirstRequest = !this.isAsyncDispatch(request);
      HttpServletRequest requestToUse = request;
      if (this.isIncludePayload() && isFirstRequest && !(request instanceof ContentCachingRequestWrapper)) {
         requestToUse = new ContentCachingRequestWrapper(request, this.getMaxPayloadLength());
      }

      boolean shouldLog = this.shouldLog(requestToUse);
      if (shouldLog && isFirstRequest) {
         this.beforeRequest(requestToUse, this.getBeforeMessage(requestToUse));
      }

      try {
         filterChain.doFilter(requestToUse, response);
      } finally {
         if (shouldLog && !this.isAsyncStarted(requestToUse)) {
            this.afterRequest(requestToUse, this.getAfterMessage(requestToUse));
         }
      }
   }

   private String getBeforeMessage(HttpServletRequest request) {
      return this.createMessage(request, this.beforeMessagePrefix, this.beforeMessageSuffix);
   }

   private String getAfterMessage(HttpServletRequest request) {
      return this.createMessage(request, this.afterMessagePrefix, this.afterMessageSuffix);
   }

   protected String createMessage(HttpServletRequest request, String prefix, String suffix) {
      StringBuilder msg = new StringBuilder();
      msg.append(prefix);
      msg.append(request.getMethod()).append(' ');
      msg.append(request.getRequestURI());
      if (this.isIncludeQueryString()) {
         String queryString = request.getQueryString();
         if (queryString != null) {
            if (this.getQueryParamPredicate() != null) {
               MultiValueMap<String, String> queryParams = UriComponentsBuilder.fromUriString("?" + queryString).build().getQueryParams();
               MultiValueMap<String, String> updatedQueryParams = new LinkedMultiValueMap(queryParams);

               for (String name : queryParams.keySet()) {
                  if (!this.getQueryParamPredicate().test(name)) {
                     updatedQueryParams.set(name, "masked");
                  }
               }

               queryString = UriComponentsBuilder.newInstance().queryParams(updatedQueryParams).build().getQuery();
            }

            msg.append('?').append(queryString);
         }
      }

      if (this.isIncludeClientInfo()) {
         String client = request.getRemoteAddr();
         if (StringUtils.hasLength(client)) {
            msg.append(", client=").append(client);
         }

         HttpSession session = request.getSession(false);
         if (session != null) {
            msg.append(", session=").append(session.getId());
         }

         String user = request.getRemoteUser();
         if (user != null) {
            msg.append(", user=").append(user);
         }
      }

      if (this.isIncludeHeaders()) {
         HttpHeaders headers = new ServletServerHttpRequest(request).getHeaders();
         if (this.getHeaderPredicate() != null) {
            Enumeration<String> names = request.getHeaderNames();

            while (names.hasMoreElements()) {
               String header = names.nextElement();
               if (!this.getHeaderPredicate().test(header)) {
                  headers.set(header, "masked");
               }
            }
         }

         msg.append(", headers=").append(headers);
      }

      if (this.isIncludePayload()) {
         String payload = this.getMessagePayload(request);
         if (payload != null) {
            msg.append(", payload=").append(payload);
         }
      }

      msg.append(suffix);
      return msg.toString();
   }

   protected @Nullable String getMessagePayload(HttpServletRequest request) {
      ContentCachingRequestWrapper wrapper = WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
      if (wrapper != null) {
         byte[] buf = wrapper.getContentAsByteArray();
         if (buf.length > 0) {
            int length = Math.min(buf.length, this.getMaxPayloadLength());

            try {
               return new String(buf, 0, length, wrapper.getCharacterEncoding());
            } catch (UnsupportedEncodingException ex) {
               return "[unknown]";
            }
         }
      }

      return null;
   }

   protected boolean shouldLog(HttpServletRequest request) {
      return true;
   }

   protected abstract void beforeRequest(HttpServletRequest request, String message);

   protected abstract void afterRequest(HttpServletRequest request, String message);
}
