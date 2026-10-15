package org.springframework.web.context.request;

public interface AsyncWebRequestInterceptor extends WebRequestInterceptor {
   void afterConcurrentHandlingStarted(WebRequest request);
}
