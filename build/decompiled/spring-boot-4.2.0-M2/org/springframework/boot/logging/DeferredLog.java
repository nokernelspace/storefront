package org.springframework.boot.logging;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public class DeferredLog implements Log {
   private @Nullable Log destination;
   private final @Nullable Supplier<Log> destinationSupplier;
   private final DeferredLog.Lines lines;

   public DeferredLog() {
      this.destinationSupplier = null;
      this.lines = new DeferredLog.Lines();
   }

   DeferredLog(Supplier<Log> destination, DeferredLog.Lines lines) {
      Assert.notNull(destination, "'destination' must not be null");
      this.destinationSupplier = destination;
      this.lines = lines;
   }

   public boolean isTraceEnabled() {
      synchronized (this.lines) {
         return this.destination == null || this.destination.isTraceEnabled();
      }
   }

   public boolean isDebugEnabled() {
      synchronized (this.lines) {
         return this.destination == null || this.destination.isDebugEnabled();
      }
   }

   public boolean isInfoEnabled() {
      synchronized (this.lines) {
         return this.destination == null || this.destination.isInfoEnabled();
      }
   }

   public boolean isWarnEnabled() {
      synchronized (this.lines) {
         return this.destination == null || this.destination.isWarnEnabled();
      }
   }

   public boolean isErrorEnabled() {
      synchronized (this.lines) {
         return this.destination == null || this.destination.isErrorEnabled();
      }
   }

   public boolean isFatalEnabled() {
      synchronized (this.lines) {
         return this.destination == null || this.destination.isFatalEnabled();
      }
   }

   public void trace(Object message) {
      this.log(LogLevel.TRACE, message, null);
   }

   public void trace(Object message, @Nullable Throwable t) {
      this.log(LogLevel.TRACE, message, t);
   }

   public void debug(Object message) {
      this.log(LogLevel.DEBUG, message, null);
   }

   public void debug(Object message, @Nullable Throwable t) {
      this.log(LogLevel.DEBUG, message, t);
   }

   public void info(Object message) {
      this.log(LogLevel.INFO, message, null);
   }

   public void info(Object message, @Nullable Throwable t) {
      this.log(LogLevel.INFO, message, t);
   }

   public void warn(Object message) {
      this.log(LogLevel.WARN, message, null);
   }

   public void warn(Object message, @Nullable Throwable t) {
      this.log(LogLevel.WARN, message, t);
   }

   public void error(Object message) {
      this.log(LogLevel.ERROR, message, null);
   }

   public void error(Object message, @Nullable Throwable t) {
      this.log(LogLevel.ERROR, message, t);
   }

   public void fatal(Object message) {
      this.log(LogLevel.FATAL, message, null);
   }

   public void fatal(Object message, @Nullable Throwable t) {
      this.log(LogLevel.FATAL, message, t);
   }

   private void log(LogLevel level, Object message, @Nullable Throwable t) {
      synchronized (this.lines) {
         if (this.destination != null) {
            level.log(this.destination, message, t);
         } else {
            this.lines.add(this.destinationSupplier, level, message, t);
         }
      }
   }

   void switchOver() {
      synchronized (this.lines) {
         Assert.state(this.destinationSupplier != null, "destinationSupplier hasn't been set");
         this.destination = this.destinationSupplier.get();
      }
   }

   public void switchTo(Class<?> destination) {
      this.switchTo(LogFactory.getLog(destination));
   }

   public void switchTo(Log destination) {
      synchronized (this.lines) {
         this.replayTo(destination);
         this.destination = destination;
      }
   }

   public void replayTo(Class<?> destination) {
      this.replayTo(LogFactory.getLog(destination));
   }

   public void replayTo(Log destination) {
      synchronized (this.lines) {
         for (DeferredLog.Line line : this.lines) {
            line.getLevel().log(destination, line.getMessage(), line.getThrowable());
         }

         this.lines.clear();
      }
   }

   public static Log replay(Log source, Class<?> destination) {
      return replay(source, LogFactory.getLog(destination));
   }

   public static Log replay(Log source, Log destination) {
      if (source instanceof DeferredLog deferredLog) {
         deferredLog.replayTo(destination);
      }

      return destination;
   }

   static class Line {
      private final @Nullable Supplier<Log> destinationSupplier;
      private final LogLevel level;
      private final Object message;
      private final @Nullable Throwable throwable;

      Line(@Nullable Supplier<Log> destinationSupplier, LogLevel level, Object message, @Nullable Throwable throwable) {
         this.destinationSupplier = destinationSupplier;
         this.level = level;
         this.message = message;
         this.throwable = throwable;
      }

      Log getDestination() {
         Assert.state(this.destinationSupplier != null, "destinationSupplier hasn't been set");
         return this.destinationSupplier.get();
      }

      LogLevel getLevel() {
         return this.level;
      }

      Object getMessage() {
         return this.message;
      }

      @Nullable Throwable getThrowable() {
         return this.throwable;
      }
   }

   static class Lines implements Iterable<DeferredLog.Line> {
      private final List<DeferredLog.Line> lines = new ArrayList<>();

      void add(@Nullable Supplier<Log> destinationSupplier, LogLevel level, Object message, @Nullable Throwable throwable) {
         this.lines.add(new DeferredLog.Line(destinationSupplier, level, message, throwable));
      }

      void clear() {
         this.lines.clear();
      }

      @Override
      public Iterator<DeferredLog.Line> iterator() {
         return this.lines.iterator();
      }
   }
}
