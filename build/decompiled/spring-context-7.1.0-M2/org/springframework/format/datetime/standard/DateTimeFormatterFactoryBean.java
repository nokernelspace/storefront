package org.springframework.format.datetime.standard;

import java.time.format.DateTimeFormatter;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;

public class DateTimeFormatterFactoryBean extends DateTimeFormatterFactory implements FactoryBean<DateTimeFormatter>, InitializingBean {
   private @Nullable DateTimeFormatter dateTimeFormatter;

   public void afterPropertiesSet() {
      this.dateTimeFormatter = this.createDateTimeFormatter();
   }

   public @Nullable DateTimeFormatter getObject() {
      return this.dateTimeFormatter;
   }

   public Class<?> getObjectType() {
      return DateTimeFormatter.class;
   }

   public boolean isSingleton() {
      return true;
   }
}
