package org.springframework.jmx.support;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.management.MalformedObjectNameException;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ObjectUtils;

public class NotificationListenerHolder {
   private @Nullable NotificationListener notificationListener;
   private @Nullable NotificationFilter notificationFilter;
   private @Nullable Object handback;
   protected @Nullable Set<Object> mappedObjectNames;

   public void setNotificationListener(@Nullable NotificationListener notificationListener) {
      this.notificationListener = notificationListener;
   }

   public @Nullable NotificationListener getNotificationListener() {
      return this.notificationListener;
   }

   public void setNotificationFilter(@Nullable NotificationFilter notificationFilter) {
      this.notificationFilter = notificationFilter;
   }

   public @Nullable NotificationFilter getNotificationFilter() {
      return this.notificationFilter;
   }

   public void setHandback(@Nullable Object handback) {
      this.handback = handback;
   }

   public @Nullable Object getHandback() {
      return this.handback;
   }

   public void setMappedObjectName(@Nullable Object mappedObjectName) {
      this.mappedObjectNames = mappedObjectName != null ? new LinkedHashSet<>(Collections.singleton(mappedObjectName)) : null;
   }

   public void setMappedObjectNames(Object... mappedObjectNames) {
      this.mappedObjectNames = new LinkedHashSet<>(Arrays.asList(mappedObjectNames));
   }

   public ObjectName @Nullable [] getResolvedObjectNames() throws MalformedObjectNameException {
      if (this.mappedObjectNames == null) {
         return null;
      }

      ObjectName[] resolved = new ObjectName[this.mappedObjectNames.size()];
      int i = 0;

      for (Object objectName : this.mappedObjectNames) {
         resolved[i] = ObjectNameManager.getInstance(objectName);
         i++;
      }

      return resolved;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof NotificationListenerHolder that
            && ObjectUtils.nullSafeEquals(this.notificationListener, that.notificationListener)
            && ObjectUtils.nullSafeEquals(this.notificationFilter, that.notificationFilter)
            && ObjectUtils.nullSafeEquals(this.handback, that.handback)
            && ObjectUtils.nullSafeEquals(this.mappedObjectNames, that.mappedObjectNames);
   }

   @Override
   public int hashCode() {
      return ObjectUtils.nullSafeHash(new Object[]{this.notificationListener, this.notificationFilter, this.handback, this.mappedObjectNames});
   }
}
