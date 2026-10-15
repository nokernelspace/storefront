package org.apache.commons.logging;

import java.lang.reflect.Constructor;
import java.util.Hashtable;
import org.apache.commons.logging.impl.NoOpLog;

@Deprecated
public class LogSource {
   protected static Hashtable<String, Log> logs = new Hashtable<>();
   protected static boolean log4jIsAvailable = isClassForName("org.apache.log4j.Logger");
   @Deprecated
   protected static boolean jdk14IsAvailable = true;
   protected static Constructor<?> logImplctor;
   private static final String[] EMPTY_STRING_ARRAY = new String[0];

   public static Log getInstance(Class<?> clazz) {
      return getInstance(clazz.getName());
   }

   public static Log getInstance(String name) {
      return logs.computeIfAbsent(name, k -> makeNewLogInstance(name));
   }

   public static String[] getLogNames() {
      return logs.keySet().toArray(EMPTY_STRING_ARRAY);
   }

   private static boolean isClassForName(String className) {
      try {
         Class.forName(className);
         return true;
      } catch (Throwable e) {
         return false;
      }
   }

   public static Log makeNewLogInstance(String name) {
      Log log;
      try {
         Object[] args = new Object[]{name};
         log = (Log)logImplctor.newInstance(args);
      } catch (Throwable t) {
         log = null;
      }

      if (null == log) {
         log = new NoOpLog(name);
      }

      return log;
   }

   public static void setLogImplementation(Class<?> logClass) throws LinkageError, ExceptionInInitializerError, NoSuchMethodException, SecurityException {
      logImplctor = logClass.getConstructor(String.class);
   }

   public static void setLogImplementation(String className) throws LinkageError, SecurityException {
      try {
         Class<?> logClass = Class.forName(className);
         logImplctor = logClass.getConstructor(String.class);
      } catch (Throwable t) {
         logImplctor = null;
      }
   }

   private LogSource() {
   }

   static {
      String name = null;

      try {
         name = System.getProperty("org.apache.commons.logging.log");
         if (name == null) {
            name = System.getProperty("org.apache.commons.logging.Log");
         }
      } catch (Throwable var7) {
      }

      if (name != null) {
         try {
            setLogImplementation(name);
         } catch (Throwable t) {
            try {
               setLogImplementation("org.apache.commons.logging.impl.NoOpLog");
            } catch (Throwable var5) {
            }
         }
      } else {
         try {
            if (log4jIsAvailable) {
               setLogImplementation("org.apache.commons.logging.impl.Log4JLogger");
            } else {
               setLogImplementation("org.apache.commons.logging.impl.Jdk14Logger");
            }
         } catch (Throwable t) {
            try {
               setLogImplementation("org.apache.commons.logging.impl.NoOpLog");
            } catch (Throwable var3) {
            }
         }
      }
   }
}
