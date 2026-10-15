package org.springframework.web.context.support;

import org.jspecify.annotations.Nullable;

public class ServletRequestHandledEvent extends RequestHandledEvent {
   private final String requestUrl;
   private final String clientAddress;
   private final String method;
   private final String servletName;
   private final int statusCode;

   public ServletRequestHandledEvent(
      Object source,
      String requestUrl,
      String clientAddress,
      String method,
      String servletName,
      @Nullable String sessionId,
      @Nullable String userName,
      long processingTimeMillis
   ) {
      super(source, sessionId, userName, processingTimeMillis);
      this.requestUrl = requestUrl;
      this.clientAddress = clientAddress;
      this.method = method;
      this.servletName = servletName;
      this.statusCode = -1;
   }

   public ServletRequestHandledEvent(
      Object source,
      String requestUrl,
      String clientAddress,
      String method,
      String servletName,
      @Nullable String sessionId,
      @Nullable String userName,
      long processingTimeMillis,
      @Nullable Throwable failureCause
   ) {
      super(source, sessionId, userName, processingTimeMillis, failureCause);
      this.requestUrl = requestUrl;
      this.clientAddress = clientAddress;
      this.method = method;
      this.servletName = servletName;
      this.statusCode = -1;
   }

   public ServletRequestHandledEvent(
      Object source,
      String requestUrl,
      String clientAddress,
      String method,
      String servletName,
      @Nullable String sessionId,
      @Nullable String userName,
      long processingTimeMillis,
      @Nullable Throwable failureCause,
      int statusCode
   ) {
      super(source, sessionId, userName, processingTimeMillis, failureCause);
      this.requestUrl = requestUrl;
      this.clientAddress = clientAddress;
      this.method = method;
      this.servletName = servletName;
      this.statusCode = statusCode;
   }

   public String getRequestUrl() {
      return this.requestUrl;
   }

   public String getClientAddress() {
      return this.clientAddress;
   }

   public String getMethod() {
      return this.method;
   }

   public String getServletName() {
      return this.servletName;
   }

   public int getStatusCode() {
      return this.statusCode;
   }

   @Override
   public String getShortDescription() {
      StringBuilder sb = new StringBuilder();
      sb.append("url=[").append(this.getRequestUrl()).append("]; ");
      sb.append("client=[").append(this.getClientAddress()).append("]; ");
      sb.append(super.getShortDescription());
      return sb.toString();
   }

   @Override
   public String getDescription() {
      StringBuilder sb = new StringBuilder();
      sb.append("url=[").append(this.getRequestUrl()).append("]; ");
      sb.append("client=[").append(this.getClientAddress()).append("]; ");
      sb.append("method=[").append(this.getMethod()).append("]; ");
      sb.append("status=[").append(this.getStatusCode()).append("]; ");
      sb.append("servlet=[").append(this.getServletName()).append("]; ");
      sb.append(super.getDescription());
      return sb.toString();
   }

   @Override
   public String toString() {
      return "ServletRequestHandledEvent: " + this.getDescription();
   }
}
