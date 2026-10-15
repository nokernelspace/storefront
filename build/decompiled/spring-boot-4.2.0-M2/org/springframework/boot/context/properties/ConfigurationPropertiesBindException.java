package org.springframework.boot.context.properties;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.util.ClassUtils;

public class ConfigurationPropertiesBindException extends BeanCreationException {
   private final ConfigurationPropertiesBean bean;

   ConfigurationPropertiesBindException(ConfigurationPropertiesBean bean, Exception cause) {
      super(bean.getName(), getMessage(bean), cause);
      this.bean = bean;
   }

   public Class<?> getBeanType() {
      return this.bean.getType();
   }

   public ConfigurationProperties getAnnotation() {
      return this.bean.getAnnotation();
   }

   private static String getMessage(ConfigurationPropertiesBean bean) {
      ConfigurationProperties annotation = bean.getAnnotation();
      StringBuilder message = new StringBuilder();
      message.append("Could not bind properties to '");
      message.append(ClassUtils.getShortName(bean.getType())).append("' : ");
      message.append("prefix=").append(annotation.prefix());
      message.append(", ignoreInvalidFields=").append(annotation.ignoreInvalidFields());
      message.append(", ignoreUnknownFields=").append(annotation.ignoreUnknownFields());
      return message.toString();
   }
}
