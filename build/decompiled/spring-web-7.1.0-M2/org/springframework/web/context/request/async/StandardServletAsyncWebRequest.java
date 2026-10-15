package org.springframework.web.context.request.async;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.context.request.ServletWebRequest;

public class StandardServletAsyncWebRequest extends ServletWebRequest implements AsyncWebRequest, AsyncListener {
   private final List<Runnable> timeoutHandlers = new ArrayList<>();
   private final List<Consumer<Throwable>> exceptionHandlers = new ArrayList<>();
   private final List<Runnable> completionHandlers = new ArrayList<>();
   private @Nullable Long timeout;
   private @Nullable AsyncContext asyncContext;
   private StandardServletAsyncWebRequest.State state;
   private final ReentrantLock stateLock = new ReentrantLock();

   public StandardServletAsyncWebRequest(HttpServletRequest request, HttpServletResponse response) {
      this(request, response, null);
   }

   StandardServletAsyncWebRequest(HttpServletRequest request, HttpServletResponse response, @Nullable StandardServletAsyncWebRequest previousRequest) {
      super(request, new StandardServletAsyncWebRequest.LifecycleHttpServletResponse(response));
      this.state = previousRequest != null ? previousRequest.state : StandardServletAsyncWebRequest.State.NEW;
      ((StandardServletAsyncWebRequest.LifecycleHttpServletResponse)this.getResponse()).setAsyncWebRequest(this);
   }

   @Override
   public void setTimeout(@Nullable Long timeout) {
      Assert.state(!this.isAsyncStarted(), "Cannot change the timeout with concurrent handling in progress");
      this.timeout = timeout;
   }

   @Override
   public void addTimeoutHandler(Runnable timeoutHandler) {
      this.timeoutHandlers.add(timeoutHandler);
   }

   @Override
   public void addErrorHandler(Consumer<Throwable> exceptionHandler) {
      this.exceptionHandlers.add(exceptionHandler);
   }

   @Override
   public void addCompletionHandler(Runnable runnable) {
      this.completionHandlers.add(runnable);
   }

   @Override
   public boolean isAsyncStarted() {
      return this.asyncContext != null && this.getRequest().isAsyncStarted();
   }

   @Override
   public boolean isAsyncComplete() {
      return this.state == StandardServletAsyncWebRequest.State.COMPLETED;
   }

   @Override
   public void startAsync() {
      Assert.state(
         this.getRequest().isAsyncSupported(),
         "Async support must be enabled on a servlet and for all filters involved in async request processing. This is done in Java code using the Servlet API or by adding \"<async-supported>true</async-supported>\" to servlet and filter declarations in web.xml."
      );
      if (!this.isAsyncStarted()) {
         if (this.state == StandardServletAsyncWebRequest.State.NEW) {
            this.state = StandardServletAsyncWebRequest.State.ASYNC;
         } else {
            Assert.state(this.state == StandardServletAsyncWebRequest.State.ASYNC, "Cannot start async: [" + this.state + "]");
         }

         this.asyncContext = this.getRequest().startAsync(this.getRequest(), this.getResponse());
         this.asyncContext.addListener(this);
         if (this.timeout != null) {
            this.asyncContext.setTimeout(this.timeout);
         }
      }
   }

   @Override
   public void dispatch() {
      Assert.state(this.asyncContext != null, "AsyncContext not yet initialized");
      if (!this.isAsyncComplete()) {
         this.asyncContext.dispatch();
      }
   }

   public void onStartAsync(AsyncEvent event) throws IOException {
   }

   public void onTimeout(AsyncEvent event) throws IOException {
      this.timeoutHandlers.forEach(Runnable::run);
   }

   public void onError(AsyncEvent event) throws IOException {
      this.stateLock.lock();

      try {
         this.state = StandardServletAsyncWebRequest.State.ERROR;
         Throwable ex = event.getThrowable();
         this.exceptionHandlers.forEach(consumer -> consumer.accept(ex));
      } finally {
         this.stateLock.unlock();
      }
   }

   public void onComplete(AsyncEvent event) throws IOException {
      this.stateLock.lock();

      try {
         this.completionHandlers.forEach(Runnable::run);
         this.asyncContext = null;
         this.state = StandardServletAsyncWebRequest.State.COMPLETED;
      } finally {
         this.stateLock.unlock();
      }
   }

   private int tryObtainLock() {
      if (this.state == StandardServletAsyncWebRequest.State.NEW) {
         return 0;
      }

      while (this.state == StandardServletAsyncWebRequest.State.ASYNC) {
         try {
            if (this.stateLock.tryLock(500L, TimeUnit.MILLISECONDS)) {
               if (this.state == StandardServletAsyncWebRequest.State.ASYNC) {
                  return 1;
               }

               this.stateLock.unlock();
               break;
            }
         } catch (InterruptedException var2) {
         }
      }

      return -1;
   }

   ReentrantLock stateLock() {
      return this.stateLock;
   }

   private static final class LifecycleHttpServletResponse extends HttpServletResponseWrapper {
      private @Nullable StandardServletAsyncWebRequest asyncWebRequest;
      private @Nullable ServletOutputStream outputStream;
      private @Nullable PrintWriter writer;

      public LifecycleHttpServletResponse(HttpServletResponse response) {
         super(response);
      }

      public void setAsyncWebRequest(StandardServletAsyncWebRequest asyncWebRequest) {
         this.asyncWebRequest = asyncWebRequest;
      }

      public ServletOutputStream getOutputStream() throws IOException {
         int level = this.obtainLockOrRaiseException();

         try {
            if (this.outputStream == null) {
               Assert.notNull(this.asyncWebRequest, "Not initialized");
               ServletOutputStream delegate = this.getResponse().getOutputStream();
               this.outputStream = new StandardServletAsyncWebRequest.LifecycleServletOutputStream(delegate, this);
            }
         } catch (IOException ex) {
            this.handleIOException(ex, "Failed to get ServletResponseOutput");
         } finally {
            this.releaseLock(level);
         }

         return this.outputStream;
      }

      public PrintWriter getWriter() throws IOException {
         int level = this.obtainLockOrRaiseException();

         try {
            if (this.writer == null) {
               Assert.notNull(this.asyncWebRequest, "Not initialized");
               this.writer = new StandardServletAsyncWebRequest.LifecyclePrintWriter(this.getResponse().getWriter(), this.asyncWebRequest);
            }
         } catch (IOException ex) {
            this.handleIOException(ex, "Failed to get PrintWriter");
         } finally {
            this.releaseLock(level);
         }

         return this.writer;
      }

      public void flushBuffer() throws IOException {
         int level = this.obtainLockOrRaiseException();

         try {
            this.getResponse().flushBuffer();
         } catch (IOException ex) {
            this.handleIOException(ex, "ServletResponse failed to flushBuffer");
         } finally {
            this.releaseLock(level);
         }
      }

      private int obtainLockOrRaiseException() throws AsyncRequestNotUsableException {
         Assert.notNull(this.asyncWebRequest, "Not initialized");
         int result = this.asyncWebRequest.tryObtainLock();
         if (result == -1) {
            throw new AsyncRequestNotUsableException(
               "Response not usable after "
                  + (this.asyncWebRequest.state == StandardServletAsyncWebRequest.State.COMPLETED ? "async request completion" : "response errors")
                  + "."
            );
         } else {
            return result;
         }
      }

      void handleIOException(IOException ex, String msg) throws AsyncRequestNotUsableException {
         Assert.notNull(this.asyncWebRequest, "Not initialized");
         this.asyncWebRequest.state = StandardServletAsyncWebRequest.State.ERROR;
         throw new AsyncRequestNotUsableException(msg + ": " + ex.getMessage(), ex);
      }

      void releaseLock(int level) {
         Assert.notNull(this.asyncWebRequest, "Not initialized");
         if (level > 0) {
            this.asyncWebRequest.stateLock.unlock();
         }
      }
   }

   private static final class LifecyclePrintWriter extends PrintWriter {
      private final PrintWriter delegate;
      private final StandardServletAsyncWebRequest asyncWebRequest;

      private LifecyclePrintWriter(PrintWriter delegate, StandardServletAsyncWebRequest asyncWebRequest) {
         super(delegate);
         this.delegate = delegate;
         this.asyncWebRequest = asyncWebRequest;
      }

      @Override
      public void flush() {
         this.invokeWithLock(this.delegate::flush);
      }

      private void invokeWithLock(Runnable runnable) {
         int level = this.asyncWebRequest.tryObtainLock();
         if (level > -1) {
            try {
               runnable.run();
            } finally {
               if (level > 0) {
                  this.asyncWebRequest.stateLock.unlock();
               }
            }
         }
      }

      @Override
      public void close() {
         this.invokeWithLock(this.delegate::close);
      }

      @Override
      public void write(int c) {
         this.invokeWithLock(() -> this.delegate.write(c));
      }

      @Override
      public void write(char[] buf, int off, int len) {
         this.invokeWithLock(() -> this.delegate.write(buf, off, len));
      }

      @Override
      public void write(char[] buf) {
         this.write(buf, 0, buf.length);
      }

      @Override
      public void write(String s, int off, int len) {
         this.invokeWithLock(() -> this.delegate.write(s, off, len));
      }

      @Override
      public void write(String s) {
         this.write(s, 0, s.length());
      }

      @Override
      public void print(boolean b) {
         this.write(String.valueOf(b));
      }

      @Override
      public void print(char c) {
         this.write(c);
      }

      @Override
      public void print(int i) {
         this.write(String.valueOf(i));
      }

      @Override
      public void print(long l) {
         this.write(String.valueOf(l));
      }

      @Override
      public void print(float f) {
         this.write(String.valueOf(f));
      }

      @Override
      public void print(double d) {
         this.write(String.valueOf(d));
      }

      @Override
      public void print(char[] s) {
         this.write(s);
      }

      @Override
      public void print(String s) {
         this.write(String.valueOf(s));
      }

      @Override
      public void print(Object obj) {
         this.write(String.valueOf(obj));
      }

      @Override
      public void println() {
         this.invokeWithLock(this.delegate::println);
      }

      @Override
      public void println(boolean x) {
         this.invokeWithLock(() -> this.delegate.println(x));
      }

      @Override
      public void println(char x) {
         this.invokeWithLock(() -> this.delegate.println(x));
      }

      @Override
      public void println(int x) {
         this.invokeWithLock(() -> this.delegate.println(x));
      }

      @Override
      public void println(long x) {
         this.invokeWithLock(() -> this.delegate.println(x));
      }

      @Override
      public void println(float x) {
         this.invokeWithLock(() -> this.delegate.println(x));
      }

      @Override
      public void println(double x) {
         this.invokeWithLock(() -> this.delegate.println(x));
      }

      @Override
      public void println(char[] x) {
         this.invokeWithLock(() -> this.delegate.println(x));
      }

      @Override
      public void println(String x) {
         this.invokeWithLock(() -> this.delegate.println(x));
      }

      @Override
      public void println(Object x) {
         this.invokeWithLock(() -> this.delegate.println(x));
      }

      @Override
      public PrintWriter printf(String format, Object... args) {
         return this.format(format, args);
      }

      @Override
      public PrintWriter printf(Locale l, String format, Object... args) {
         return this.format(format, args);
      }

      @Override
      public PrintWriter format(String format, Object... args) {
         this.invokeWithLock(() -> this.delegate.format(format, args));
         return this;
      }

      @Override
      public PrintWriter format(Locale l, String format, Object... args) {
         this.invokeWithLock(() -> this.delegate.format(format, args));
         return this;
      }
   }

   private static final class LifecycleServletOutputStream extends ServletOutputStream {
      private final ServletOutputStream delegate;
      private final StandardServletAsyncWebRequest.LifecycleHttpServletResponse response;

      private LifecycleServletOutputStream(ServletOutputStream delegate, StandardServletAsyncWebRequest.LifecycleHttpServletResponse response) {
         this.delegate = delegate;
         this.response = response;
      }

      public boolean isReady() {
         return this.delegate.isReady();
      }

      public void setWriteListener(WriteListener writeListener) {
         this.delegate.setWriteListener(writeListener);
      }

      public void write(int b) throws IOException {
         int level = this.response.obtainLockOrRaiseException();

         try {
            this.delegate.write(b);
         } catch (IOException ex) {
            this.response.handleIOException(ex, "ServletOutputStream failed to write");
         } finally {
            this.response.releaseLock(level);
         }
      }

      public void write(byte[] buf, int offset, int len) throws IOException {
         int level = this.response.obtainLockOrRaiseException();

         try {
            this.delegate.write(buf, offset, len);
         } catch (IOException ex) {
            this.response.handleIOException(ex, "ServletOutputStream failed to write");
         } finally {
            this.response.releaseLock(level);
         }
      }

      public void flush() throws IOException {
         int level = this.response.obtainLockOrRaiseException();

         try {
            this.delegate.flush();
         } catch (IOException ex) {
            this.response.handleIOException(ex, "ServletOutputStream failed to flush");
         } finally {
            this.response.releaseLock(level);
         }
      }

      public void close() throws IOException {
         int level = this.response.obtainLockOrRaiseException();

         try {
            this.delegate.close();
         } catch (IOException ex) {
            this.response.handleIOException(ex, "ServletOutputStream failed to close");
         } finally {
            this.response.releaseLock(level);
         }
      }
   }

   private enum State {
      NEW,
      ASYNC,
      ERROR,
      COMPLETED;
   }
}
