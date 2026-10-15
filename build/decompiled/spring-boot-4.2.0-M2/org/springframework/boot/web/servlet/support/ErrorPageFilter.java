package org.springframework.boot.web.servlet.support;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.web.error.ErrorPage;
import org.springframework.boot.web.error.ErrorPageRegistry;
import org.springframework.core.Ordered;
import org.springframework.util.ClassUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class ErrorPageFilter implements Filter, ErrorPageRegistry, Ordered {
   private static final Log logger = LogFactory.getLog(ErrorPageFilter.class);
   private static final String ERROR_EXCEPTION = "jakarta.servlet.error.exception";
   private static final String ERROR_EXCEPTION_TYPE = "jakarta.servlet.error.exception_type";
   private static final String ERROR_MESSAGE = "jakarta.servlet.error.message";
   public static final String ERROR_REQUEST_URI = "jakarta.servlet.error.request_uri";
   private static final String ERROR_STATUS_CODE = "jakarta.servlet.error.status_code";
   private static final Set<Class<?>> CLIENT_ABORT_EXCEPTIONS;
   private @Nullable String global;
   private final Map<Integer, String> statuses = new HashMap<>();
   private final Map<@Nullable Class<?>, String> exceptions = new HashMap<>();
   private final OncePerRequestFilter delegate = new OncePerRequestFilter() {
      protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
         ErrorPageFilter.this.doFilter(request, response, chain);
      }

      protected boolean shouldNotFilterAsyncDispatch() {
         return false;
      }
   };

   public void init(FilterConfig filterConfig) throws ServletException {
      this.delegate.init(filterConfig);
   }

   public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
      this.delegate.doFilter(request, response, chain);
   }

   private void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
      ErrorPageFilter.ErrorWrapperResponse wrapped = new ErrorPageFilter.ErrorWrapperResponse(response);

      try {
         chain.doFilter(request, wrapped);
         if (wrapped.hasErrorToSend()) {
            this.handleErrorStatus(request, response, wrapped.getStatus(), wrapped.getMessage());
            response.flushBuffer();
         } else if (!request.isAsyncStarted() && !response.isCommitted()) {
            response.flushBuffer();
         }
      } catch (Throwable ex) {
         Throwable exceptionToHandle = ex;
         if (ex instanceof ServletException servletException) {
            Throwable rootCause = servletException.getRootCause();
            if (rootCause != null) {
               exceptionToHandle = rootCause;
            }
         }

         this.handleException(request, response, wrapped, exceptionToHandle);
         response.flushBuffer();
      }
   }

   private void handleErrorStatus(HttpServletRequest request, HttpServletResponse response, int status, @Nullable String message) throws ServletException, IOException {
      if (response.isCommitted()) {
         this.handleCommittedResponse(request, null);
      } else {
         String errorPath = this.getErrorPath(this.statuses, status);
         if (errorPath == null) {
            response.sendError(status, message);
         } else {
            response.setStatus(status);
            this.setErrorAttributes(request, status, message);
            request.getRequestDispatcher(errorPath).forward(request, response);
         }
      }
   }

   private void handleException(HttpServletRequest request, HttpServletResponse response, ErrorPageFilter.ErrorWrapperResponse wrapped, Throwable ex) throws IOException, ServletException {
      Class<?> type = ex.getClass();
      String errorPath = this.getErrorPath(type);
      if (errorPath == null) {
         this.rethrow(ex);
      } else if (response.isCommitted()) {
         this.handleCommittedResponse(request, ex);
      } else {
         this.forwardToErrorPage(errorPath, request, wrapped, ex);
      }
   }

   private void forwardToErrorPage(String path, HttpServletRequest request, HttpServletResponse response, Throwable ex) throws ServletException, IOException {
      if (logger.isErrorEnabled()) {
         String message = "Forwarding to error page from request " + this.getDescription(request) + " due to exception [" + ex.getMessage() + "]";
         logger.error(message, ex);
      }

      this.setErrorAttributes(request, 500, ex.getMessage());
      request.setAttribute("jakarta.servlet.error.exception", ex);
      request.setAttribute("jakarta.servlet.error.exception_type", ex.getClass());
      response.reset();
      response.setStatus(500);
      request.getRequestDispatcher(path).forward(request, response);
      request.removeAttribute("jakarta.servlet.error.exception");
      request.removeAttribute("jakarta.servlet.error.exception_type");
   }

   protected String getDescription(HttpServletRequest request) {
      String pathInfo = request.getPathInfo() != null ? request.getPathInfo() : "";
      return "[" + request.getServletPath() + pathInfo + "]";
   }

   private void handleCommittedResponse(HttpServletRequest request, @Nullable Throwable ex) {
      if (!this.isClientAbortException(ex)) {
         String message = "Cannot forward to error page for request "
            + this.getDescription(request)
            + " as the response has already been committed. As a result, the response may have the wrong status code. If your application is running on WebSphere Application Server you may be able to resolve this problem by setting com.ibm.ws.webcontainer.invokeFlushAfterService to false";
         if (ex == null) {
            logger.error(message);
         } else {
            logger.error(message, ex);
         }
      }
   }

   private boolean isClientAbortException(@Nullable Throwable ex) {
      if (ex == null) {
         return false;
      }

      for (Class<?> candidate : CLIENT_ABORT_EXCEPTIONS) {
         if (candidate.isInstance(ex)) {
            return true;
         }
      }

      return this.isClientAbortException(ex.getCause());
   }

   private @Nullable String getErrorPath(Map<Integer, String> map, Integer status) {
      return map.containsKey(status) ? map.get(status) : this.global;
   }

   private @Nullable String getErrorPath(Class<?> type) {
      while (type != Object.class) {
         String path = this.exceptions.get(type);
         if (path != null) {
            return path;
         }

         type = type.getSuperclass();
      }

      return this.global;
   }

   private void setErrorAttributes(HttpServletRequest request, int status, @Nullable String message) {
      request.setAttribute("jakarta.servlet.error.status_code", status);
      request.setAttribute("jakarta.servlet.error.message", message);
      request.setAttribute("jakarta.servlet.error.request_uri", request.getRequestURI());
   }

   private void rethrow(Throwable ex) throws IOException, ServletException {
      if (ex instanceof RuntimeException runtimeException) {
         throw runtimeException;
      } else if (ex instanceof Error error) {
         throw error;
      } else if (ex instanceof IOException ioException) {
         throw ioException;
      } else if (ex instanceof ServletException servletException) {
         throw servletException;
      } else {
         throw new IllegalStateException(ex);
      }
   }

   @Override
   public void addErrorPages(ErrorPage... errorPages) {
      for (ErrorPage errorPage : errorPages) {
         if (errorPage.isGlobal()) {
            this.global = errorPage.getPath();
         } else if (errorPage.getStatus() != null) {
            this.statuses.put(errorPage.getStatus().value(), errorPage.getPath());
         } else {
            this.exceptions.put(errorPage.getException(), errorPage.getPath());
         }
      }
   }

   public void destroy() {
   }

   public int getOrder() {
      return -2147483647;
   }

   private static void addClassIfPresent(Collection<Class<?>> collection, String className) {
      try {
         collection.add(ClassUtils.forName(className, null));
      } catch (Throwable var3) {
      }
   }

   static {
      Set<Class<?>> clientAbortExceptions = new HashSet<>();
      addClassIfPresent(clientAbortExceptions, "org.apache.catalina.connector.ClientAbortException");
      CLIENT_ABORT_EXCEPTIONS = Collections.unmodifiableSet(clientAbortExceptions);
   }

   private static class ErrorWrapperResponse extends HttpServletResponseWrapper {
      private int status;
      private @Nullable String message;
      private boolean hasErrorToSend;

      ErrorWrapperResponse(HttpServletResponse response) {
         super(response);
      }

      public void sendError(int status) {
         this.sendError(status, null);
      }

      public void sendError(int status, @Nullable String message) {
         this.status = status;
         this.message = message;
         this.hasErrorToSend = true;
      }

      public int getStatus() {
         return this.hasErrorToSend ? this.status : super.getStatus();
      }

      public void flushBuffer() throws IOException {
         this.sendErrorIfNecessary();
         super.flushBuffer();
      }

      private void sendErrorIfNecessary() throws IOException {
         if (this.hasErrorToSend && !this.isCommitted()) {
            ((HttpServletResponse)this.getResponse()).sendError(this.status, this.message);
         }
      }

      @Nullable String getMessage() {
         return this.message;
      }

      boolean hasErrorToSend() {
         return this.hasErrorToSend;
      }

      public PrintWriter getWriter() throws IOException {
         this.sendErrorIfNecessary();
         return super.getWriter();
      }

      public ServletOutputStream getOutputStream() throws IOException {
         this.sendErrorIfNecessary();
         return super.getOutputStream();
      }
   }
}
