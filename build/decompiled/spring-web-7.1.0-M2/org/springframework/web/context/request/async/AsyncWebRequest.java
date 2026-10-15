package org.springframework.web.context.request.async;

import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.web.context.request.NativeWebRequest;

public interface AsyncWebRequest extends NativeWebRequest {
   void setTimeout(@Nullable Long timeout);

   void addTimeoutHandler(Runnable runnable);

   void addErrorHandler(Consumer<Throwable> exceptionHandler);

   void addCompletionHandler(Runnable runnable);

   void startAsync();

   boolean isAsyncStarted();

   void dispatch();

   boolean isAsyncComplete();
}
