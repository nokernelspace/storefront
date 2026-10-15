package org.springframework.boot.logging.logback;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.status.OnConsoleStatusListener;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.util.StatusListenerConfigHelper;
import java.io.PrintStream;
import java.util.List;

final class SystemStatusListener extends OnConsoleStatusListener {
   private static final long RETROSPECTIVE_THRESHOLD = 300L;
   private final boolean debug;

   private SystemStatusListener(boolean debug) {
      this.debug = debug;
      this.setResetResistant(false);
      this.setRetrospective(0L);
   }

   public void start() {
      super.start();
      this.retrospectivePrint();
   }

   private void retrospectivePrint() {
      if (this.context != null) {
         long now = System.currentTimeMillis();
         List<Status> statusList = this.context.getStatusManager().getCopyOfStatusList();
         statusList.stream().filter(status -> getElapsedTime(status, now) < 300L).forEach(this::addStatusEvent);
      }
   }

   public void addStatusEvent(Status status) {
      if (this.debug || status.getLevel() >= 1) {
         super.addStatusEvent(status);
      }
   }

   protected PrintStream getPrintStream() {
      return !this.debug ? System.err : System.out;
   }

   private static long getElapsedTime(Status status, long now) {
      return now - status.getTimestamp();
   }

   static void addTo(LoggerContext loggerContext) {
      addTo(loggerContext, false);
   }

   static void addTo(LoggerContext loggerContext, boolean debug) {
      StatusListenerConfigHelper.addOnConsoleListenerInstance(loggerContext, new SystemStatusListener(debug));
   }
}
