package org.springframework.boot;

import org.springframework.context.ApplicationEvent;

public class ExitCodeEvent extends ApplicationEvent {
   private final int exitCode;

   public ExitCodeEvent(Object source, int exitCode) {
      super(source);
      this.exitCode = exitCode;
   }

   public int getExitCode() {
      return this.exitCode;
   }
}
