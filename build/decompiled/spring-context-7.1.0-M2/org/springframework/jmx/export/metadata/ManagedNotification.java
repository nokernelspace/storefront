package org.springframework.jmx.export.metadata;

import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

public class ManagedNotification {
   private String @Nullable [] notificationTypes;
   private @Nullable String name;
   private @Nullable String description;

   public void setNotificationType(String notificationType) {
      this.notificationTypes = StringUtils.commaDelimitedListToStringArray(notificationType);
   }

   public void setNotificationTypes(String @Nullable ... notificationTypes) {
      this.notificationTypes = notificationTypes;
   }

   public String @Nullable [] getNotificationTypes() {
      return this.notificationTypes;
   }

   public void setName(@Nullable String name) {
      this.name = name;
   }

   public @Nullable String getName() {
      return this.name;
   }

   public void setDescription(@Nullable String description) {
      this.description = description;
   }

   public @Nullable String getDescription() {
      return this.description;
   }
}
