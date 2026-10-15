package org.springframework.web.context.request.async;

import java.util.concurrent.Callable;
import org.springframework.web.context.request.NativeWebRequest;

public class TimeoutCallableProcessingInterceptor implements CallableProcessingInterceptor {
   @Override
   public <T> Object handleTimeout(NativeWebRequest request, Callable<T> task) throws Exception {
      return new AsyncRequestTimeoutException();
   }
}
