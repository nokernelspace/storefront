package org.springframework.jmx.export.notification;

import org.springframework.beans.factory.Aware;

public interface NotificationPublisherAware extends Aware {
   void setNotificationPublisher(NotificationPublisher notificationPublisher);
}
