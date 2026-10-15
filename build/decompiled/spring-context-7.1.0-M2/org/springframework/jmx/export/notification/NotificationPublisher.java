package org.springframework.jmx.export.notification;

import javax.management.Notification;

@FunctionalInterface
public interface NotificationPublisher {
   void sendNotification(Notification notification) throws UnableToSendNotificationException;
}
