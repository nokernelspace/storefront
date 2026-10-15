package org.springframework.jmx.export.assembler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.management.modelmbean.ModelMBeanNotificationInfo;
import org.jspecify.annotations.Nullable;
import org.springframework.jmx.export.metadata.JmxMetadataUtils;
import org.springframework.jmx.export.metadata.ManagedNotification;
import org.springframework.util.StringUtils;

public abstract class AbstractConfigurableMBeanInfoAssembler extends AbstractReflectiveMBeanInfoAssembler {
   private ModelMBeanNotificationInfo @Nullable [] notificationInfos;
   private final Map<String, ModelMBeanNotificationInfo[]> notificationInfoMappings = new HashMap<>();

   public void setNotificationInfos(ManagedNotification[] notificationInfos) {
      ModelMBeanNotificationInfo[] infos = new ModelMBeanNotificationInfo[notificationInfos.length];

      for (int i = 0; i < notificationInfos.length; i++) {
         ManagedNotification notificationInfo = notificationInfos[i];
         infos[i] = JmxMetadataUtils.convertToModelMBeanNotificationInfo(notificationInfo);
      }

      this.notificationInfos = infos;
   }

   public void setNotificationInfoMappings(Map<String, Object> notificationInfoMappings) {
      notificationInfoMappings.forEach((beanKey, result) -> this.notificationInfoMappings.put(beanKey, this.extractNotificationMetadata(result)));
   }

   @Override
   protected ModelMBeanNotificationInfo[] getNotificationInfo(Object managedBean, String beanKey) {
      ModelMBeanNotificationInfo[] result = null;
      if (StringUtils.hasText(beanKey)) {
         result = this.notificationInfoMappings.get(beanKey);
      }

      if (result == null) {
         result = this.notificationInfos;
      }

      return result != null ? result : new ModelMBeanNotificationInfo[0];
   }

   private ModelMBeanNotificationInfo[] extractNotificationMetadata(Object mapValue) {
      if (mapValue instanceof ManagedNotification mn) {
         return new ModelMBeanNotificationInfo[]{JmxMetadataUtils.convertToModelMBeanNotificationInfo(mn)};
      } else if (mapValue instanceof Collection<?> col) {
         List<ModelMBeanNotificationInfo> result = new ArrayList<>();

         for (Object colValue : col) {
            if (!(colValue instanceof ManagedNotification mn)) {
               throw new IllegalArgumentException("Property 'notificationInfoMappings' only accepts ManagedNotifications for Map values");
            }

            result.add(JmxMetadataUtils.convertToModelMBeanNotificationInfo(mn));
         }

         return result.toArray(new ModelMBeanNotificationInfo[0]);
      } else {
         throw new IllegalArgumentException("Property 'notificationInfoMappings' only accepts ManagedNotifications for Map values");
      }
   }
}
