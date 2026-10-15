package org.springframework.web.context.request.async;

import org.springframework.web.context.request.NativeWebRequest;

public class TimeoutDeferredResultProcessingInterceptor implements DeferredResultProcessingInterceptor {
   @Override
   public <T> boolean handleTimeout(NativeWebRequest request, DeferredResult<T> result) throws Exception {
      result.setErrorResult(new AsyncRequestTimeoutException());
      return false;
   }
}
