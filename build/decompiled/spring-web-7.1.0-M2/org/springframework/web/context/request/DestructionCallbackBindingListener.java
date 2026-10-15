package org.springframework.web.context.request;

import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionBindingListener;
import java.io.Serializable;

public class DestructionCallbackBindingListener implements HttpSessionBindingListener, Serializable {
   private final Runnable destructionCallback;

   public DestructionCallbackBindingListener(Runnable destructionCallback) {
      this.destructionCallback = destructionCallback;
   }

   public void valueBound(HttpSessionBindingEvent event) {
   }

   public void valueUnbound(HttpSessionBindingEvent event) {
      this.destructionCallback.run();
   }
}
