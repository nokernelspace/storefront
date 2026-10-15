package org.springframework.boot.logging;

import java.io.File;
import java.util.Properties;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.PropertyResolver;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class LogFile {
   public static final String FILE_NAME_PROPERTY = "logging.file.name";
   public static final String FILE_PATH_PROPERTY = "logging.file.path";
   private final @Nullable String file;
   private final @Nullable String path;

   LogFile(String file) {
      this(file, null);
   }

   LogFile(@Nullable String file, @Nullable String path) {
      Assert.isTrue(StringUtils.hasLength(file) || StringUtils.hasLength(path), "'file' or 'path' must not be empty");
      this.file = file;
      this.path = path;
   }

   public void applyToSystemProperties() {
      this.applyTo(System.getProperties());
   }

   public void applyTo(Properties properties) {
      this.put(properties, LoggingSystemProperty.LOG_PATH, this.path);
      this.put(properties, LoggingSystemProperty.LOG_FILE, this.toString());
   }

   private void put(Properties properties, LoggingSystemProperty property, @Nullable String value) {
      if (StringUtils.hasLength(value)) {
         properties.put(property.getEnvironmentVariableName(), value);
      }
   }

   @Override
   public String toString() {
      return StringUtils.hasLength(this.file) ? this.file : new File(this.path, "spring.log").getPath();
   }

   public static @Nullable LogFile get(PropertyResolver propertyResolver) {
      String file = propertyResolver.getProperty("logging.file.name");
      String path = propertyResolver.getProperty("logging.file.path");
      return !StringUtils.hasLength(file) && !StringUtils.hasLength(path) ? null : new LogFile(file, path);
   }
}
