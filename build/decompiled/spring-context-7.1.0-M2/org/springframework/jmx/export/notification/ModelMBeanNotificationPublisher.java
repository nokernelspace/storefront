package org.springframework.jmx.export.notification;

import javax.management.AttributeChangeNotification;
import javax.management.MBeanException;
import javax.management.Notification;
import javax.management.ObjectName;
import javax.management.modelmbean.ModelMBeanNotificationBroadcaster;
import org.springframework.util.Assert;

public class ModelMBeanNotificationPublisher implements NotificationPublisher {
   private final ModelMBeanNotificationBroadcaster modelMBean;
   private final ObjectName objectName;
   private final Object managedResource;

   public ModelMBeanNotificationPublisher(ModelMBeanNotificationBroadcaster modelMBean, ObjectName objectName, Object managedResource) {
      Assert.notNull(modelMBean, "'modelMBean' must not be null");
      Assert.notNull(objectName, "'objectName' must not be null");
      Assert.notNull(managedResource, "'managedResource' must not be null");
      this.modelMBean = modelMBean;
      this.objectName = objectName;
      this.managedResource = managedResource;
   }

   @Override
   public void sendNotification(Notification notification) {
      Assert.notNull(notification, "Notification must not be null");
      this.replaceNotificationSourceIfNecessary(notification);

      try {
         if (notification instanceof AttributeChangeNotification acn) {
            this.modelMBean.sendAttributeChangeNotification(acn);
         } else {
            this.modelMBean.sendNotification(notification);
         }
      } catch (MBeanException ex) {
         throw new UnableToSendNotificationException("Unable to send notification [" + notification + "]", ex);
      }
   }

   private void replaceNotificationSourceIfNecessary(Notification notification) {
      if (notification.getSource() == null || notification.getSource().equals(this.managedResource)) {
         notification.setSource(this.objectName);
      }
   }
}
