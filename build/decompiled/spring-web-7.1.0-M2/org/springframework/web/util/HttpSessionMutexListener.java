package org.springframework.web.util;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import java.io.Serializable;

public class HttpSessionMutexListener implements HttpSessionListener {
   public void sessionCreated(HttpSessionEvent event) {
      event.getSession().setAttribute(WebUtils.SESSION_MUTEX_ATTRIBUTE, new HttpSessionMutexListener.Mutex());
   }

   public void sessionDestroyed(HttpSessionEvent event) {
      event.getSession().removeAttribute(WebUtils.SESSION_MUTEX_ATTRIBUTE);
   }

   private static class Mutex implements Serializable {
   }
}
