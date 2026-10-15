package org.apache.commons.logging.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.security.AccessController;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogConfigurationException;

public class SimpleLog implements Log, Serializable {
   private static final long serialVersionUID = 136942970684951178L;
   protected static final String systemPrefix = "org.apache.commons.logging.simplelog.";
   protected static final Properties simpleLogProps = new Properties();
   protected static final String DEFAULT_DATE_TIME_FORMAT = "yyyy/MM/dd HH:mm:ss:SSS zzz";
   protected static volatile boolean showLogName;
   protected static volatile boolean showShortName = true;
   protected static volatile boolean showDateTime;
   protected static volatile String dateTimeFormat = "yyyy/MM/dd HH:mm:ss:SSS zzz";
   protected static DateFormat dateFormatter;
   public static final int LOG_LEVEL_TRACE = 1;
   public static final int LOG_LEVEL_DEBUG = 2;
   public static final int LOG_LEVEL_INFO = 3;
   public static final int LOG_LEVEL_WARN = 4;
   public static final int LOG_LEVEL_ERROR = 5;
   public static final int LOG_LEVEL_FATAL = 6;
   public static final int LOG_LEVEL_ALL = 0;
   public static final int LOG_LEVEL_OFF = 7;
   protected volatile String logName;
   protected volatile int currentLogLevel;
   private volatile String shortLogName;

   private static boolean getBooleanProperty(String name, boolean defaultValue) {
      String prop = getStringProperty(name);
      return prop == null ? defaultValue : Boolean.parseBoolean(prop);
   }

   private static ClassLoader getContextClassLoader() {
      ClassLoader classLoader = null;

      try {
         classLoader = Thread.currentThread().getContextClassLoader();
      } catch (RuntimeException e) {
         if (!(e instanceof SecurityException)) {
            throw new LogConfigurationException("Unexpected SecurityException", e);
         }
      }

      if (classLoader == null) {
         classLoader = SimpleLog.class.getClassLoader();
      }

      return classLoader;
   }

   private static InputStream getResourceAsStream(String name) {
      return AccessController.doPrivileged(() -> {
         ClassLoader threadCL = getContextClassLoader();
         return threadCL != null ? threadCL.getResourceAsStream(name) : ClassLoader.getSystemResourceAsStream(name);
      });
   }

   private static String getStringProperty(String name) {
      String prop = null;

      try {
         prop = System.getProperty(name);
      } catch (SecurityException var3) {
      }

      return prop == null ? simpleLogProps.getProperty(name) : prop;
   }

   private static String getStringProperty(String name, String defaultValue) {
      String prop = getStringProperty(name);
      return prop == null ? defaultValue : prop;
   }

   public SimpleLog(String name) {
      this.logName = name;
      this.setLevel(3);
      String level = getStringProperty("org.apache.commons.logging.simplelog.log." + this.logName);

      for (int i = String.valueOf(name).lastIndexOf("."); level == null && i > -1; i = String.valueOf(name).lastIndexOf(".")) {
         name = name.substring(0, i);
         level = getStringProperty("org.apache.commons.logging.simplelog.log." + name);
      }

      if (level == null) {
         level = getStringProperty("org.apache.commons.logging.simplelog.defaultlog");
      }

      if (level != null) {
         level = level.toLowerCase(Locale.ROOT);
      }

      if (level != null) {
         switch (level) {
            case "all":
               this.setLevel(0);
               break;
            case "trace":
               this.setLevel(1);
               break;
            case "debug":
               this.setLevel(2);
               break;
            case "info":
               this.setLevel(3);
               break;
            case "warn":
               this.setLevel(4);
               break;
            case "error":
               this.setLevel(5);
               break;
            case "fatal":
               this.setLevel(6);
               break;
            case "off":
               this.setLevel(7);
         }
      }
   }

   @Override
   public final void debug(Object message) {
      if (this.isLevelEnabled(2)) {
         this.log(2, message, null);
      }
   }

   @Override
   public final void debug(Object message, Throwable t) {
      if (this.isLevelEnabled(2)) {
         this.log(2, message, t);
      }
   }

   @Override
   public final void error(Object message) {
      if (this.isLevelEnabled(5)) {
         this.log(5, message, null);
      }
   }

   @Override
   public final void error(Object message, Throwable t) {
      if (this.isLevelEnabled(5)) {
         this.log(5, message, t);
      }
   }

   @Override
   public final void fatal(Object message) {
      if (this.isLevelEnabled(6)) {
         this.log(6, message, null);
      }
   }

   @Override
   public final void fatal(Object message, Throwable t) {
      if (this.isLevelEnabled(6)) {
         this.log(6, message, t);
      }
   }

   public int getLevel() {
      return this.currentLogLevel;
   }

   @Override
   public final void info(Object message) {
      if (this.isLevelEnabled(3)) {
         this.log(3, message, null);
      }
   }

   @Override
   public final void info(Object message, Throwable t) {
      if (this.isLevelEnabled(3)) {
         this.log(3, message, t);
      }
   }

   @Override
   public final boolean isDebugEnabled() {
      return this.isLevelEnabled(2);
   }

   @Override
   public final boolean isErrorEnabled() {
      return this.isLevelEnabled(5);
   }

   @Override
   public final boolean isFatalEnabled() {
      return this.isLevelEnabled(6);
   }

   @Override
   public final boolean isInfoEnabled() {
      return this.isLevelEnabled(3);
   }

   protected boolean isLevelEnabled(int logLevel) {
      return logLevel >= this.currentLogLevel;
   }

   @Override
   public final boolean isTraceEnabled() {
      return this.isLevelEnabled(1);
   }

   @Override
   public final boolean isWarnEnabled() {
      return this.isLevelEnabled(4);
   }

   protected void log(int type, Object message, Throwable t) {
      StringBuilder buf = new StringBuilder();
      if (showDateTime) {
         Date now = new Date();
         String dateText;
         synchronized (dateFormatter) {
            dateText = dateFormatter.format(now);
         }

         buf.append(dateText);
         buf.append(" ");
      }

      switch (type) {
         case 1:
            buf.append("[TRACE] ");
            break;
         case 2:
            buf.append("[DEBUG] ");
            break;
         case 3:
            buf.append("[INFO] ");
            break;
         case 4:
            buf.append("[WARN] ");
            break;
         case 5:
            buf.append("[ERROR] ");
            break;
         case 6:
            buf.append("[FATAL] ");
            break;
         default:
            buf.append("[UNDEFINED] ");
      }

      if (showShortName) {
         if (this.shortLogName == null) {
            String slName = this.logName.substring(this.logName.lastIndexOf(".") + 1);
            this.shortLogName = slName.substring(slName.lastIndexOf("/") + 1);
         }

         buf.append(String.valueOf(this.shortLogName)).append(" - ");
      } else if (showLogName) {
         buf.append(String.valueOf(this.logName)).append(" - ");
      }

      buf.append(String.valueOf(message));
      if (t != null) {
         buf.append(" <");
         buf.append(t.toString());
         buf.append(">");
         StringWriter sw = new StringWriter(1024);
         PrintWriter pw = new PrintWriter(sw);

         try {
            t.printStackTrace(pw);
         } catch (Throwable var10) {
            try {
               pw.close();
            } catch (Throwable var9) {
               var10.addSuppressed(var9);
            }

            throw var10;
         }

         pw.close();
         buf.append(sw.toString());
      }

      this.write(buf);
   }

   public void setLevel(int currentLogLevel) {
      this.currentLogLevel = currentLogLevel;
   }

   @Override
   public final void trace(Object message) {
      if (this.isLevelEnabled(1)) {
         this.log(1, message, null);
      }
   }

   @Override
   public final void trace(Object message, Throwable t) {
      if (this.isLevelEnabled(1)) {
         this.log(1, message, t);
      }
   }

   @Override
   public final void warn(Object message) {
      if (this.isLevelEnabled(4)) {
         this.log(4, message, null);
      }
   }

   @Override
   public final void warn(Object message, Throwable t) {
      if (this.isLevelEnabled(4)) {
         this.log(4, message, t);
      }
   }

   private void write(Object buffer) {
      System.err.println(Objects.toString(buffer));
   }

   protected void write(StringBuffer buffer) {
      System.err.println(Objects.toString(buffer));
   }

   static {
      try {
         InputStream in = getResourceAsStream("simplelog.properties");

         try {
            if (null != in) {
               simpleLogProps.load(in);
            }
         } catch (Throwable var5) {
            if (in != null) {
               try {
                  in.close();
               } catch (Throwable var4) {
                  var5.addSuppressed(var4);
               }
            }

            throw var5;
         }

         if (in != null) {
            in.close();
         }
      } catch (IOException var6) {
      }

      showLogName = getBooleanProperty("org.apache.commons.logging.simplelog.showlogname", showLogName);
      showShortName = getBooleanProperty("org.apache.commons.logging.simplelog.showShortLogname", showShortName);
      showDateTime = getBooleanProperty("org.apache.commons.logging.simplelog.showdatetime", showDateTime);
      if (showDateTime) {
         dateTimeFormat = getStringProperty("org.apache.commons.logging.simplelog.dateTimeFormat", dateTimeFormat);

         try {
            dateFormatter = new SimpleDateFormat(dateTimeFormat);
         } catch (IllegalArgumentException e) {
            dateTimeFormat = "yyyy/MM/dd HH:mm:ss:SSS zzz";
            dateFormatter = new SimpleDateFormat(dateTimeFormat);
         }
      }
   }
}
