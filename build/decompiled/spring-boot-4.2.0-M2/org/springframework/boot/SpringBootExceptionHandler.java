package org.springframework.boot;

import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

class SpringBootExceptionHandler implements UncaughtExceptionHandler {
   private static final Set<String> LOG_CONFIGURATION_MESSAGES;
   private static final SpringBootExceptionHandler.LoggedExceptionHandlerThreadLocal handler = new SpringBootExceptionHandler.LoggedExceptionHandlerThreadLocal();
   private final @Nullable UncaughtExceptionHandler parent;
   private final List<Throwable> loggedExceptions = new ArrayList<>();
   private int exitCode;

   SpringBootExceptionHandler(@Nullable UncaughtExceptionHandler parent) {
      this.parent = parent;
   }

   void registerLoggedException(Throwable exception) {
      this.loggedExceptions.add(exception);
   }

   void registerExitCode(int exitCode) {
      this.exitCode = exitCode;
   }

   @Override
   public void uncaughtException(Thread thread, Throwable ex) {
      try {
         if (this.isPassedToParent(ex) && this.parent != null) {
            this.parent.uncaughtException(thread, ex);
         }
      } finally {
         this.loggedExceptions.clear();
         if (this.exitCode != 0) {
            System.exit(this.exitCode);
         }
      }
   }

   private boolean isPassedToParent(Throwable ex) {
      return this.isLogConfigurationMessage(ex) || !this.isRegistered(ex);
   }

   private boolean isLogConfigurationMessage(@Nullable Throwable ex) {
      if (ex == null) {
         return false;
      }

      if (ex instanceof InvocationTargetException) {
         return this.isLogConfigurationMessage(ex.getCause());
      }

      String message = ex.getMessage();
      if (message != null) {
         for (String candidate : LOG_CONFIGURATION_MESSAGES) {
            if (message.contains(candidate)) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean isRegistered(@Nullable Throwable ex) {
      if (ex == null) {
         return false;
      } else if (this.loggedExceptions.contains(ex)) {
         return true;
      } else {
         return ex instanceof InvocationTargetException ? this.isRegistered(ex.getCause()) : false;
      }
   }

   static SpringBootExceptionHandler forCurrentThread() {
      return handler.get();
   }

   static {
      Set<String> messages = new HashSet<>();
      messages.add("Logback configuration error detected");
      LOG_CONFIGURATION_MESSAGES = Collections.unmodifiableSet(messages);
   }

   private static final class LoggedExceptionHandlerThreadLocal extends ThreadLocal<SpringBootExceptionHandler> {
      protected SpringBootExceptionHandler initialValue() {
         SpringBootExceptionHandler handler = new SpringBootExceptionHandler(Thread.currentThread().getUncaughtExceptionHandler());
         Thread.currentThread().setUncaughtExceptionHandler(handler);
         return handler;
      }
   }
}
