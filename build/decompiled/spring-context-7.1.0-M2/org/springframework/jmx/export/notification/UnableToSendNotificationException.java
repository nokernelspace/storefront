package org.springframework.jmx.export.notification;

import org.springframework.jmx.JmxException;

public class UnableToSendNotificationException extends JmxException {
   public UnableToSendNotificationException(String msg) {
      super(msg);
   }

   public UnableToSendNotificationException(String msg, Throwable cause) {
      super(msg, cause);
   }
}
