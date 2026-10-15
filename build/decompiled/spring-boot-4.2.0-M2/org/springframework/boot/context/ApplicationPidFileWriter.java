package org.springframework.boot.context;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.SpringApplicationEvent;
import org.springframework.boot.system.ApplicationPid;
import org.springframework.boot.system.SystemProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

public class ApplicationPidFileWriter implements ApplicationListener<SpringApplicationEvent>, Ordered {
   private static final Log logger = LogFactory.getLog(ApplicationPidFileWriter.class);
   private static final String DEFAULT_FILE_NAME = "application.pid";
   private static final List<ApplicationPidFileWriter.Property> FILE_PROPERTIES;
   private static final List<ApplicationPidFileWriter.Property> FAIL_ON_WRITE_ERROR_PROPERTIES;
   private static final AtomicBoolean created = new AtomicBoolean();
   private int order = -2147483635;
   private final File file;
   private Class<? extends SpringApplicationEvent> triggerEventType = ApplicationPreparedEvent.class;

   public ApplicationPidFileWriter() {
      this(new File("application.pid"));
   }

   public ApplicationPidFileWriter(String filename) {
      this(new File(filename));
   }

   public ApplicationPidFileWriter(File file) {
      Assert.notNull(file, "'file' must not be null");
      this.file = file;
   }

   public void setTriggerEventType(Class<? extends SpringApplicationEvent> triggerEventType) {
      Assert.notNull(triggerEventType, "'triggerEventType' must not be null");
      this.triggerEventType = triggerEventType;
   }

   public void onApplicationEvent(SpringApplicationEvent event) {
      if (this.triggerEventType.isInstance(event) && created.compareAndSet(false, true)) {
         try {
            this.writePidFile(event);
         } catch (Exception ex) {
            String message = String.format("Cannot create pid file %s", this.file);
            if (this.failOnWriteError(event)) {
               throw new IllegalStateException(message, ex);
            }

            logger.warn(message, ex);
         }
      }
   }

   private void writePidFile(SpringApplicationEvent event) throws IOException {
      File pidFile = this.file;
      String override = this.getProperty(event, FILE_PROPERTIES);
      if (override != null) {
         pidFile = new File(override);
      }

      new ApplicationPid().write(pidFile);
      pidFile.deleteOnExit();
   }

   private boolean failOnWriteError(SpringApplicationEvent event) {
      String value = this.getProperty(event, FAIL_ON_WRITE_ERROR_PROPERTIES);
      return Boolean.parseBoolean(value);
   }

   private @Nullable String getProperty(SpringApplicationEvent event, List<ApplicationPidFileWriter.Property> candidates) {
      for (ApplicationPidFileWriter.Property candidate : candidates) {
         String value = candidate.getValue(event);
         if (value != null) {
            return value;
         }
      }

      return null;
   }

   public void setOrder(int order) {
      this.order = order;
   }

   public int getOrder() {
      return this.order;
   }

   protected static void reset() {
      created.set(false);
   }

   static {
      List<ApplicationPidFileWriter.Property> properties = new ArrayList<>();
      properties.add(new ApplicationPidFileWriter.SpringProperty("spring.pid.", "file"));
      properties.add(new ApplicationPidFileWriter.SpringProperty("spring.", "pidfile"));
      properties.add(new ApplicationPidFileWriter.SystemProperty("PIDFILE"));
      FILE_PROPERTIES = Collections.unmodifiableList(properties);
      properties = new ArrayList<>();
      properties.add(new ApplicationPidFileWriter.SpringProperty("spring.pid.", "fail-on-write-error"));
      properties.add(new ApplicationPidFileWriter.SystemProperty("PID_FAIL_ON_WRITE_ERROR"));
      FAIL_ON_WRITE_ERROR_PROPERTIES = Collections.unmodifiableList(properties);
   }

   private interface Property {
      @Nullable String getValue(SpringApplicationEvent event);
   }

   private static class SpringProperty implements ApplicationPidFileWriter.Property {
      private final String prefix;
      private final String key;

      SpringProperty(String prefix, String key) {
         this.prefix = prefix;
         this.key = key;
      }

      @Override
      public @Nullable String getValue(SpringApplicationEvent event) {
         Environment environment = this.getEnvironment(event);
         return environment == null ? null : environment.getProperty(this.prefix + this.key);
      }

      private @Nullable Environment getEnvironment(SpringApplicationEvent event) {
         if (event instanceof ApplicationEnvironmentPreparedEvent environmentPreparedEvent) {
            return environmentPreparedEvent.getEnvironment();
         } else if (event instanceof ApplicationPreparedEvent preparedEvent) {
            return preparedEvent.getApplicationContext().getEnvironment();
         } else {
            return event instanceof ApplicationReadyEvent readyEvent ? readyEvent.getApplicationContext().getEnvironment() : null;
         }
      }
   }

   private static class SystemProperty implements ApplicationPidFileWriter.Property {
      private final String[] properties;

      SystemProperty(String name) {
         this.properties = new String[]{name.toUpperCase(Locale.ENGLISH), name.toLowerCase(Locale.ENGLISH)};
      }

      @Override
      public @Nullable String getValue(SpringApplicationEvent event) {
         return SystemProperties.get(this.properties);
      }
   }
}
