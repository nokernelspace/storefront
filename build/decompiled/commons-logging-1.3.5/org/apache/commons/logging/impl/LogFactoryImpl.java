package org.apache.commons.logging.impl;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.AccessController;
import java.util.Hashtable;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogConfigurationException;
import org.apache.commons.logging.LogFactory;

public class LogFactoryImpl extends LogFactory {
   private static final String LOGGING_IMPL_LOG4J_LOGGER = "org.apache.commons.logging.impl.Log4JLogger";
   private static final String LOGGING_IMPL_JDK14_LOGGER = "org.apache.commons.logging.impl.Jdk14Logger";
   private static final String LOGGING_IMPL_LUMBERJACK_LOGGER = "org.apache.commons.logging.impl.Jdk13LumberjackLogger";
   private static final String LOGGING_IMPL_SIMPLE_LOGGER = "org.apache.commons.logging.impl.SimpleLog";
   private static final String PKG_IMPL = "org.apache.commons.logging.impl.";
   private static final int PKG_LEN = "org.apache.commons.logging.impl.".length();
   private static final String[] EMPTY_STRING_ARRAY = new String[0];
   public static final String LOG_PROPERTY = "org.apache.commons.logging.Log";
   protected static final String LOG_PROPERTY_OLD = "org.apache.commons.logging.log";
   public static final String ALLOW_FLAWED_CONTEXT_PROPERTY = "org.apache.commons.logging.Log.allowFlawedContext";
   public static final String ALLOW_FLAWED_DISCOVERY_PROPERTY = "org.apache.commons.logging.Log.allowFlawedDiscovery";
   public static final String ALLOW_FLAWED_HIERARCHY_PROPERTY = "org.apache.commons.logging.Log.allowFlawedHierarchy";
   private static final String[] classesToDiscover = new String[]{"org.apache.commons.logging.impl.Jdk14Logger", "org.apache.commons.logging.impl.SimpleLog"};
   private boolean useTCCL = true;
   private String diagnosticPrefix;
   protected Hashtable<String, Object> attributes = new Hashtable<>();
   protected Hashtable<String, Log> instances = new Hashtable<>();
   private String logClassName;
   protected Constructor<?> logConstructor;
   protected Class<?>[] logConstructorSignature = new Class[]{String.class};
   protected Method logMethod;
   protected Class<?>[] logMethodSignature = new Class[]{LogFactory.class};
   private boolean allowFlawedContext;
   private boolean allowFlawedDiscovery;
   private boolean allowFlawedHierarchy;

   protected static ClassLoader getClassLoader(Class<?> clazz) {
      return LogFactory.getClassLoader(clazz);
   }

   protected static ClassLoader getContextClassLoader() throws LogConfigurationException {
      return LogFactory.getContextClassLoader();
   }

   private static ClassLoader getContextClassLoaderInternal() throws LogConfigurationException {
      return AccessController.doPrivileged(() -> LogFactory.directGetContextClassLoader());
   }

   private static String getSystemProperty(String key, String def) throws SecurityException {
      return AccessController.doPrivileged(() -> System.getProperty(key, def));
   }

   protected static boolean isDiagnosticsEnabled() {
      return LogFactory.isDiagnosticsEnabled();
   }

   private static String trim(String src) {
      return src == null ? null : src.trim();
   }

   public LogFactoryImpl() {
      this.initDiagnostics();
      if (isDiagnosticsEnabled()) {
         this.logDiagnostic("Instance created.");
      }
   }

   private Log createLogFromClass(String logAdapterClassName, String logCategory, boolean affectState) throws LogConfigurationException {
      if (isDiagnosticsEnabled()) {
         this.logDiagnostic("Attempting to instantiate '" + logAdapterClassName + "'");
      }

      Object[] params = new Object[]{logCategory};
      Log logAdapter = null;
      Constructor<?> constructor = null;
      Class<?> logAdapterClass = null;
      ClassLoader currentCL = this.getBaseClassLoader();

      while (true) {
         this.logDiagnostic("Trying to load '" + logAdapterClassName + "' from class loader " + objectId(currentCL));

         try {
            if (isDiagnosticsEnabled()) {
               String resourceName = logAdapterClassName.replace('.', '/') + ".class";
               URL url;
               if (currentCL != null) {
                  url = currentCL.getResource(resourceName);
               } else {
                  url = ClassLoader.getSystemResource(resourceName + ".class");
               }

               if (url == null) {
                  this.logDiagnostic("Class '" + logAdapterClassName + "' [" + resourceName + "] cannot be found.");
               } else {
                  this.logDiagnostic("Class '" + logAdapterClassName + "' was found at '" + url + "'");
               }
            }

            Class<?> clazz;
            try {
               clazz = Class.forName(logAdapterClassName, true, currentCL);
            } catch (ClassNotFoundException originalClassNotFoundException) {
               String msg = originalClassNotFoundException.getMessage();
               this.logDiagnostic("The log adapter '" + logAdapterClassName + "' is not available via class loader " + objectId(currentCL) + ": " + trim(msg));

               try {
                  clazz = Class.forName(logAdapterClassName);
               } catch (ClassNotFoundException secondaryClassNotFoundException) {
                  msg = secondaryClassNotFoundException.getMessage();
                  this.logDiagnostic("The log adapter '" + logAdapterClassName + "' is not available via the LogFactoryImpl class class loader: " + trim(msg));
                  break;
               }
            }

            constructor = clazz.getConstructor(this.logConstructorSignature);
            Object o = constructor.newInstance(params);
            if (o instanceof Log) {
               logAdapterClass = clazz;
               logAdapter = (Log)o;
               break;
            }

            this.handleFlawedHierarchy(currentCL, clazz);
         } catch (NoClassDefFoundError e) {
            String msg = e.getMessage();
            this.logDiagnostic(
               "The log adapter '" + logAdapterClassName + "' is missing dependencies when loaded via class loader " + objectId(currentCL) + ": " + trim(msg)
            );
            break;
         } catch (ExceptionInInitializerError e) {
            String msg = e.getMessage();
            this.logDiagnostic(
               "The log adapter '"
                  + logAdapterClassName
                  + "' is unable to initialize itself when loaded via class loader "
                  + objectId(currentCL)
                  + ": "
                  + trim(msg)
            );
            break;
         } catch (LogConfigurationException e) {
            throw e;
         } catch (Throwable t) {
            handleThrowable(t);
            this.handleFlawedDiscovery(logAdapterClassName, t);
         }

         if (currentCL == null) {
            break;
         }

         currentCL = this.getParentClassLoader(currentCL);
      }

      if (logAdapterClass != null && affectState) {
         this.logClassName = logAdapterClassName;
         this.logConstructor = constructor;

         try {
            this.logMethod = logAdapterClass.getMethod("setLogFactory", this.logMethodSignature);
            this.logDiagnostic("Found method setLogFactory(LogFactory) in '" + logAdapterClassName + "'");
         } catch (Throwable t) {
            handleThrowable(t);
            this.logMethod = null;
            this.logDiagnostic(
               "[INFO] '" + logAdapterClassName + "' from class loader " + objectId(currentCL) + " does not declare optional method setLogFactory(LogFactory)"
            );
         }

         this.logDiagnostic(
            "Log adapter '" + logAdapterClassName + "' from class loader " + objectId(logAdapterClass.getClassLoader()) + " has been selected for use."
         );
      }

      return logAdapter;
   }

   private Log discoverLogImplementation(String logCategory) throws LogConfigurationException {
      if (isDiagnosticsEnabled()) {
         this.logDiagnostic("Discovering a Log implementation...");
      }

      this.initConfiguration();
      Log result = null;
      String specifiedLogClassName = this.findUserSpecifiedLogClassName();
      if (specifiedLogClassName != null) {
         if (isDiagnosticsEnabled()) {
            this.logDiagnostic("Attempting to load user-specified log class '" + specifiedLogClassName + "'...");
         }

         result = this.createLogFromClass(specifiedLogClassName, logCategory, true);
         if (result == null) {
            StringBuilder messageBuffer = new StringBuilder("User-specified log class '");
            messageBuffer.append(specifiedLogClassName);
            messageBuffer.append("' cannot be found or is not useable.");
            this.informUponSimilarName(messageBuffer, specifiedLogClassName, "org.apache.commons.logging.impl.Log4JLogger");
            this.informUponSimilarName(messageBuffer, specifiedLogClassName, "org.apache.commons.logging.impl.Jdk14Logger");
            this.informUponSimilarName(messageBuffer, specifiedLogClassName, "org.apache.commons.logging.impl.Jdk13LumberjackLogger");
            this.informUponSimilarName(messageBuffer, specifiedLogClassName, "org.apache.commons.logging.impl.SimpleLog");
            throw new LogConfigurationException(messageBuffer.toString());
         } else {
            return result;
         }
      } else {
         if (isDiagnosticsEnabled()) {
            this.logDiagnostic("No user-specified Log implementation; performing discovery using the standard supported logging implementations...");
         }

         for (int i = 0; i < classesToDiscover.length && result == null; i++) {
            result = this.createLogFromClass(classesToDiscover[i], logCategory, true);
         }

         if (result == null) {
            throw new LogConfigurationException("No suitable Log implementation");
         } else {
            return result;
         }
      }
   }

   private String findUserSpecifiedLogClassName() {
      if (isDiagnosticsEnabled()) {
         this.logDiagnostic("Trying to get log class from attribute 'org.apache.commons.logging.Log'");
      }

      String specifiedClass = (String)this.getAttribute("org.apache.commons.logging.Log");
      if (specifiedClass == null) {
         if (isDiagnosticsEnabled()) {
            this.logDiagnostic("Trying to get log class from attribute 'org.apache.commons.logging.log'");
         }

         specifiedClass = (String)this.getAttribute("org.apache.commons.logging.log");
      }

      if (specifiedClass == null) {
         if (isDiagnosticsEnabled()) {
            this.logDiagnostic("Trying to get log class from system property 'org.apache.commons.logging.Log'");
         }

         try {
            specifiedClass = getSystemProperty("org.apache.commons.logging.Log", null);
         } catch (SecurityException e) {
            if (isDiagnosticsEnabled()) {
               this.logDiagnostic("No access allowed to system property 'org.apache.commons.logging.Log' - " + e.getMessage());
            }
         }
      }

      if (specifiedClass == null) {
         if (isDiagnosticsEnabled()) {
            this.logDiagnostic("Trying to get log class from system property 'org.apache.commons.logging.log'");
         }

         try {
            specifiedClass = getSystemProperty("org.apache.commons.logging.log", null);
         } catch (SecurityException e) {
            if (isDiagnosticsEnabled()) {
               this.logDiagnostic("No access allowed to system property 'org.apache.commons.logging.log' - " + e.getMessage());
            }
         }
      }

      if (specifiedClass != null) {
         specifiedClass = specifiedClass.trim();
      }

      return specifiedClass;
   }

   @Override
   public Object getAttribute(String name) {
      return this.attributes.get(name);
   }

   @Override
   public String[] getAttributeNames() {
      return this.attributes.keySet().toArray(EMPTY_STRING_ARRAY);
   }

   private ClassLoader getBaseClassLoader() throws LogConfigurationException {
      ClassLoader thisClassLoader = getClassLoader(LogFactoryImpl.class);
      if (!this.useTCCL) {
         return thisClassLoader;
      }

      ClassLoader contextClassLoader = getContextClassLoaderInternal();
      ClassLoader baseClassLoader = this.getLowestClassLoader(contextClassLoader, thisClassLoader);
      if (baseClassLoader == null) {
         if (!this.allowFlawedContext) {
            throw new LogConfigurationException(
               "Bad class loader hierarchy; LogFactoryImpl was loaded via a class loader that is not related to the current context class loader."
            );
         }

         if (isDiagnosticsEnabled()) {
            this.logDiagnostic(
               "[WARNING] the context class loader is not part of a parent-child relationship with the class loader that loaded LogFactoryImpl."
            );
         }

         return contextClassLoader;
      } else {
         if (baseClassLoader != contextClassLoader) {
            if (!this.allowFlawedContext) {
               throw new LogConfigurationException(
                  "Bad class loader hierarchy; LogFactoryImpl was loaded via a class loader that is not related to the current context class loader."
               );
            }

            if (isDiagnosticsEnabled()) {
               this.logDiagnostic(
                  "Warning: the context class loader is an ancestor of the class loader that loaded LogFactoryImpl; it should be the same or a descendant. The application using commons-logging should ensure the context class loader is used correctly."
               );
            }
         }

         return baseClassLoader;
      }
   }

   private boolean getBooleanConfiguration(String key, boolean dflt) {
      String val = this.getConfigurationValue(key);
      return val == null ? dflt : Boolean.parseBoolean(val);
   }

   private String getConfigurationValue(String property) {
      if (isDiagnosticsEnabled()) {
         this.logDiagnostic("[ENV] Trying to get configuration for item " + property);
      }

      Object valueObj = this.getAttribute(property);
      if (valueObj != null) {
         if (isDiagnosticsEnabled()) {
            this.logDiagnostic("[ENV] Found LogFactory attribute [" + valueObj + "] for " + property);
         }

         return valueObj.toString();
      } else {
         if (isDiagnosticsEnabled()) {
            this.logDiagnostic("[ENV] No LogFactory attribute found for " + property);
         }

         try {
            String value = getSystemProperty(property, null);
            if (value != null) {
               if (isDiagnosticsEnabled()) {
                  this.logDiagnostic("[ENV] Found system property [" + value + "] for " + property);
               }

               return value;
            }

            if (isDiagnosticsEnabled()) {
               this.logDiagnostic("[ENV] No system property found for property " + property);
            }
         } catch (SecurityException e) {
            if (isDiagnosticsEnabled()) {
               this.logDiagnostic("[ENV] Security prevented reading system property " + property);
            }
         }

         if (isDiagnosticsEnabled()) {
            this.logDiagnostic("[ENV] No configuration defined for item " + property);
         }

         return null;
      }
   }

   @Override
   public Log getInstance(Class<?> clazz) throws LogConfigurationException {
      return this.getInstance(clazz.getName());
   }

   @Override
   public Log getInstance(String name) throws LogConfigurationException {
      return this.instances.computeIfAbsent(name, this::newInstance);
   }

   @Deprecated
   protected String getLogClassName() {
      if (this.logClassName == null) {
         this.discoverLogImplementation(this.getClass().getName());
      }

      return this.logClassName;
   }

   @Deprecated
   protected Constructor<?> getLogConstructor() throws LogConfigurationException {
      if (this.logConstructor == null) {
         this.discoverLogImplementation(this.getClass().getName());
      }

      return this.logConstructor;
   }

   private ClassLoader getLowestClassLoader(ClassLoader c1, ClassLoader c2) {
      if (c1 == null) {
         return c2;
      }

      if (c2 == null) {
         return c1;
      }

      ClassLoader current = c1;

      while (current != null) {
         if (current == c2) {
            return c1;
         }

         current = this.getParentClassLoader(current);
      }

      for (ClassLoader var4 = c2; var4 != null; var4 = this.getParentClassLoader(var4)) {
         if (var4 == c1) {
            return c2;
         }
      }

      return null;
   }

   private ClassLoader getParentClassLoader(ClassLoader cl) {
      try {
         return AccessController.doPrivileged(() -> cl.getParent());
      } catch (SecurityException ex) {
         this.logDiagnostic("[SECURITY] Unable to obtain parent class loader");
         return null;
      }
   }

   private void handleFlawedDiscovery(String logAdapterClassName, Throwable discoveryFlaw) {
      if (isDiagnosticsEnabled()) {
         this.logDiagnostic(
            "Could not instantiate Log '" + logAdapterClassName + "' -- " + discoveryFlaw.getClass().getName() + ": " + discoveryFlaw.getLocalizedMessage()
         );
         if (discoveryFlaw instanceof InvocationTargetException) {
            InvocationTargetException ite = (InvocationTargetException)discoveryFlaw;
            Throwable cause = ite.getTargetException();
            if (cause != null) {
               this.logDiagnostic("... InvocationTargetException: " + cause.getClass().getName() + ": " + cause.getLocalizedMessage());
               if (cause instanceof ExceptionInInitializerError) {
                  ExceptionInInitializerError eiie = (ExceptionInInitializerError)cause;
                  Throwable cause2 = eiie.getCause();
                  if (cause2 != null) {
                     StringWriter sw = new StringWriter();
                     cause2.printStackTrace(new PrintWriter(sw, true));
                     this.logDiagnostic("... ExceptionInInitializerError: " + sw.toString());
                  }
               }
            }
         }
      }

      if (!this.allowFlawedDiscovery) {
         throw new LogConfigurationException(discoveryFlaw);
      }
   }

   private void handleFlawedHierarchy(ClassLoader badClassLoader, Class<?> badClass) throws LogConfigurationException {
      boolean implementsLog = false;
      String logInterfaceName = Log.class.getName();
      Class<?>[] interfaces = badClass.getInterfaces();

      for (Class<?> element : interfaces) {
         if (logInterfaceName.equals(element.getName())) {
            implementsLog = true;
            break;
         }
      }

      if (implementsLog) {
         if (isDiagnosticsEnabled()) {
            try {
               ClassLoader logInterfaceClassLoader = getClassLoader(Log.class);
               this.logDiagnostic(
                  "Class '"
                     + badClass.getName()
                     + "' was found in class loader "
                     + objectId(badClassLoader)
                     + ". It is bound to a Log interface which is not the one loaded from class loader "
                     + objectId(logInterfaceClassLoader)
               );
            } catch (Throwable t) {
               handleThrowable(t);
               this.logDiagnostic("Error while trying to output diagnostics about bad class '" + badClass + "'");
            }
         }

         if (!this.allowFlawedHierarchy) {
            StringBuilder msg = new StringBuilder();
            msg.append("Terminating logging for this context ");
            msg.append("due to bad log hierarchy. ");
            msg.append("You have more than one version of '");
            msg.append(Log.class.getName());
            msg.append("' visible.");
            if (isDiagnosticsEnabled()) {
               this.logDiagnostic(msg.toString());
            }

            throw new LogConfigurationException(msg.toString());
         }

         if (isDiagnosticsEnabled()) {
            StringBuilder msg = new StringBuilder();
            msg.append("Warning: bad log hierarchy. ");
            msg.append("You have more than one version of '");
            msg.append(Log.class.getName());
            msg.append("' visible.");
            this.logDiagnostic(msg.toString());
         }
      } else {
         if (!this.allowFlawedDiscovery) {
            StringBuilder msg = new StringBuilder();
            msg.append("Terminating logging for this context. ");
            msg.append("Log class '");
            msg.append(badClass.getName());
            msg.append("' does not implement the Log interface.");
            if (isDiagnosticsEnabled()) {
               this.logDiagnostic(msg.toString());
            }

            throw new LogConfigurationException(msg.toString());
         }

         if (isDiagnosticsEnabled()) {
            StringBuilder msg = new StringBuilder();
            msg.append("[WARNING] Log class '");
            msg.append(badClass.getName());
            msg.append("' does not implement the Log interface.");
            this.logDiagnostic(msg.toString());
         }
      }
   }

   private void informUponSimilarName(StringBuilder messageBuffer, String name, String candidate) {
      if (!name.equals(candidate)) {
         if (name.regionMatches(true, 0, candidate, 0, PKG_LEN + 5)) {
            messageBuffer.append(" Did you mean '");
            messageBuffer.append(candidate);
            messageBuffer.append("'?");
         }
      }
   }

   private void initConfiguration() {
      this.allowFlawedContext = this.getBooleanConfiguration("org.apache.commons.logging.Log.allowFlawedContext", true);
      this.allowFlawedDiscovery = this.getBooleanConfiguration("org.apache.commons.logging.Log.allowFlawedDiscovery", true);
      this.allowFlawedHierarchy = this.getBooleanConfiguration("org.apache.commons.logging.Log.allowFlawedHierarchy", true);
   }

   private void initDiagnostics() {
      Class<LogFactoryImpl> clazz = (Class<LogFactoryImpl>)this.getClass();
      ClassLoader classLoader = getClassLoader(clazz);

      String classLoaderName;
      try {
         if (classLoader == null) {
            classLoaderName = "BOOTLOADER";
         } else {
            classLoaderName = objectId(classLoader);
         }
      } catch (SecurityException e) {
         classLoaderName = "UNKNOWN";
      }

      this.diagnosticPrefix = "[LogFactoryImpl@" + System.identityHashCode(this) + " from " + classLoaderName + "] ";
   }

   @Deprecated
   protected boolean isJdk13LumberjackAvailable() {
      return this.isLogLibraryAvailable("Jdk13Lumberjack", "org.apache.commons.logging.impl.Jdk13LumberjackLogger");
   }

   @Deprecated
   protected boolean isJdk14Available() {
      return this.isLogLibraryAvailable("Jdk14", "org.apache.commons.logging.impl.Jdk14Logger");
   }

   @Deprecated
   protected boolean isLog4JAvailable() {
      return this.isLogLibraryAvailable("Log4J", "org.apache.commons.logging.impl.Log4JLogger");
   }

   private boolean isLogLibraryAvailable(String name, String className) {
      if (isDiagnosticsEnabled()) {
         this.logDiagnostic("Checking for '" + name + "'.");
      }

      try {
         Log log = this.createLogFromClass(className, this.getClass().getName(), false);
         if (log == null) {
            if (isDiagnosticsEnabled()) {
               this.logDiagnostic("Did not find '" + name + "'.");
            }

            return false;
         } else {
            if (isDiagnosticsEnabled()) {
               this.logDiagnostic("Found '" + name + "'.");
            }

            return true;
         }
      } catch (LogConfigurationException e) {
         if (isDiagnosticsEnabled()) {
            this.logDiagnostic("Logging system '" + name + "' is available but not useable.");
         }

         return false;
      }
   }

   protected void logDiagnostic(String msg) {
      if (isDiagnosticsEnabled()) {
         logRawDiagnostic(this.diagnosticPrefix + msg);
      }
   }

   protected Log newInstance(String name) throws LogConfigurationException {
      try {
         Log instance;
         if (this.logConstructor == null) {
            instance = this.discoverLogImplementation(name);
         } else {
            Object[] params = new Object[]{name};
            instance = (Log)this.logConstructor.newInstance(params);
         }

         if (this.logMethod != null) {
            Object[] params = new Object[]{this};
            this.logMethod.invoke(instance, params);
         }

         return instance;
      } catch (LogConfigurationException lce) {
         throw lce;
      } catch (InvocationTargetException e) {
         Throwable c = e.getTargetException();
         throw new LogConfigurationException(c == null ? e : c);
      } catch (Throwable t) {
         handleThrowable(t);
         throw new LogConfigurationException(t);
      }
   }

   @Override
   public void release() {
      this.logDiagnostic("Releasing all known loggers");
      this.instances.clear();
   }

   @Override
   public void removeAttribute(String name) {
      this.attributes.remove(name);
   }

   @Override
   public void setAttribute(String name, Object value) {
      if (this.logConstructor != null) {
         this.logDiagnostic("setAttribute: call too late; configuration already performed.");
      }

      if (value == null) {
         this.attributes.remove(name);
      } else {
         this.attributes.put(name, value);
      }

      if (name.equals("use_tccl")) {
         this.useTCCL = value != null && Boolean.parseBoolean(value.toString());
      }
   }
}
