package org.springframework.web.context.support;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEvent;

public class RequestHandledEvent extends ApplicationEvent {
   private final @Nullable String sessionId;
   private final @Nullable String userName;
   private final long processingTimeMillis;
   private @Nullable Throwable failureCause;

   public RequestHandledEvent(Object source, @Nullable String sessionId, @Nullable String userName, long processingTimeMillis) {
      super(source);
      this.sessionId = sessionId;
      this.userName = userName;
      this.processingTimeMillis = processingTimeMillis;
   }

   public RequestHandledEvent(Object source, @Nullable String sessionId, @Nullable String userName, long processingTimeMillis, @Nullable Throwable failureCause) {
      this(source, sessionId, userName, processingTimeMillis);
      this.failureCause = failureCause;
   }

   public long getProcessingTimeMillis() {
      return this.processingTimeMillis;
   }

   public @Nullable String getSessionId() {
      return this.sessionId;
   }

   public @Nullable String getUserName() {
      return this.userName;
   }

   public boolean wasFailure() {
      return this.failureCause != null;
   }

   public @Nullable Throwable getFailureCause() {
      return this.failureCause;
   }

   public String getShortDescription() {
      StringBuilder sb = new StringBuilder();
      sb.append("session=[").append(this.sessionId).append("]; ");
      sb.append("user=[").append(this.userName).append("]; ");
      return sb.toString();
   }

   public String getDescription() {
      StringBuilder sb = new StringBuilder();
      sb.append("session=[").append(this.sessionId).append("]; ");
      sb.append("user=[").append(this.userName).append("]; ");
      sb.append("time=[").append(this.processingTimeMillis).append("ms]");
      if (this.wasFailure()) {
         sb.append("; failure=[").append(this.failureCause).append("]");
      }

      return sb.toString();
   }

   public String toString() {
      return "RequestHandledEvent: " + this.getDescription();
   }
}
