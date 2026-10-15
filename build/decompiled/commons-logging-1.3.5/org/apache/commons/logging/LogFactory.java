package org.apache.commons.logging;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Objects;
import java.util.Properties;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.function.Supplier;

public abstract class LogFactory {
   public static final String PRIORITY_KEY = "priority";
   public static final String TCCL_KEY = "use_tccl";
   public static final String FACTORY_PROPERTY = "org.apache.commons.logging.LogFactory";
   private static final String FACTORY_LOG4J_API = "org.apache.commons.logging.impl.Log4jApiLogFactory";
   private static final String LOG4J_TO_SLF4J_BRIDGE = "org.apache.logging.slf4j.SLF4JProvider";
   private static final String FACTORY_SLF4J = "org.apache.commons.logging.impl.Slf4jLogFactory";
   public static final String FACTORY_DEFAULT = "org.apache.commons.logging.impl.LogFactoryImpl";
   public static final String FACTORY_PROPERTIES = "commons-logging.properties";
   protected static final String SERVICE_ID = "META-INF/services/org.apache.commons.logging.LogFactory";
   public static final String DIAGNOSTICS_DEST_PROPERTY = "org.apache.commons.logging.diagnostics.dest";
   private static final PrintStream DIAGNOSTICS_STREAM;
   private static final String DIAGNOSTICS_PREFIX;
   public static final String HASHTABLE_IMPLEMENTATION_PROPERTY = "org.apache.commons.logging.LogFactory.HashtableImpl";
   private static final String WEAK_HASHTABLE_CLASSNAME = "org.apache.commons.logging.impl.WeakHashtable";
   private static final WeakReference<ClassLoader> thisClassLoaderRef;
   private static final int MAX_BROKEN_SERVICES = 3;
   protected static Hashtable<ClassLoader, LogFactory> factories;
   @Deprecated
   protected static volatile LogFactory nullClassLoaderFactory;

   private static void cacheFactory(ClassLoader classLoader, LogFactory factory) {
      if (factory != null) {
         if (classLoader == null) {
            nullClassLoaderFactory = factory;
         } else {
            factories.put(classLoader, factory);
         }
      }
   }

   protected static Object createFactory(String factoryClassName, ClassLoader classLoader) {
      Class<?> logFactoryClass = null;

      try {
         if (classLoader != null) {
            try {
               logFactoryClass = classLoader.loadClass(factoryClassName);
               if (LogFactory.class.isAssignableFrom(logFactoryClass)) {
                  if (isDiagnosticsEnabled()) {
                     logDiagnostic("Loaded class " + logFactoryClass.getName() + " from class loader " + objectId(classLoader));
                  }
               } else if (isDiagnosticsEnabled()) {
                  logDiagnostic(
                     "Factory class "
                        + logFactoryClass.getName()
                        + " loaded from class loader "
                        + objectId(logFactoryClass.getClassLoader())
                        + " does not extend '"
                        + LogFactory.class.getName()
                        + "' as loaded by this class loader."
                  );
                  logHierarchy("[BAD CL TREE] ", classLoader);
               }

               return LogFactory.class.cast(logFactoryClass.getConstructor().newInstance());
            } catch (ClassNotFoundException ex) {
               if (classLoader == thisClassLoaderRef.get()) {
                  if (isDiagnosticsEnabled()) {
                     logDiagnostic("Unable to locate any class called '" + factoryClassName + "' via class loader " + objectId(classLoader));
                  }

                  throw ex;
               }
            } catch (NoClassDefFoundError e) {
               if (classLoader == thisClassLoaderRef.get()) {
                  if (isDiagnosticsEnabled()) {
                     logDiagnostic(
                        "Class '"
                           + factoryClassName
                           + "' cannot be loaded via class loader "
                           + objectId(classLoader)
                           + " - it depends on some other class that cannot be found."
                     );
                  }

                  throw e;
               }
            } catch (ClassCastException e) {
               if (classLoader == thisClassLoaderRef.get()) {
                  boolean implementsLogFactory = implementsLogFactory(logFactoryClass);
                  StringBuilder msg = new StringBuilder();
                  msg.append("The application has specified that a custom LogFactory implementation should be used but Class '");
                  msg.append(factoryClassName);
                  msg.append("' cannot be converted to '");
                  msg.append(LogFactory.class.getName());
                  msg.append("'. ");
                  if (implementsLogFactory) {
                     msg.append("The conflict is caused by the presence of multiple LogFactory classes in incompatible class loaders. Background can");
                     msg.append(" be found in https://commons.apache.org/logging/tech.html. If you have not explicitly specified a custom LogFactory");
                     msg.append(" then it is likely that the container has set one without your knowledge. In this case, consider using the ");
                     msg.append("commons-logging-adapters.jar file or specifying the standard LogFactory from the command line. ");
                  } else {
                     msg.append("Please check the custom implementation. ");
                  }

                  msg.append("Help can be found at https://commons.apache.org/logging/troubleshooting.html.");
                  logDiagnostic(msg.toString());
                  throw new ClassCastException(msg.toString());
               }
            }
         }

         if (isDiagnosticsEnabled()) {
            logDiagnostic(
               "Unable to load factory class via class loader " + objectId(classLoader) + " - trying the class loader associated with this LogFactory."
            );
         }

         logFactoryClass = Class.forName(factoryClassName);
         return LogFactory.class.cast(logFactoryClass.getConstructor().newInstance());
      } catch (Exception e) {
         if (isDiagnosticsEnabled()) {
            logDiagnostic("Unable to create LogFactory instance.");
         }

         return logFactoryClass != null && !LogFactory.class.isAssignableFrom(logFactoryClass)
            ? new LogConfigurationException("The chosen LogFactory implementation does not extend LogFactory. Please check your configuration.", e)
            : new LogConfigurationException(e);
      }
   }

   private static Hashtable<ClassLoader, LogFactory> createFactoryStore() {
      Hashtable<ClassLoader, LogFactory> result = null;

      String storeImplementationClass;
      try {
         storeImplementationClass = getSystemProperty("org.apache.commons.logging.LogFactory.HashtableImpl", null);
      } catch (SecurityException ex) {
         storeImplementationClass = null;
      }

      if (storeImplementationClass == null) {
         storeImplementationClass = "org.apache.commons.logging.impl.WeakHashtable";
      }

      try {
         Class<Hashtable<ClassLoader, LogFactory>> implementationClass = (Class<Hashtable<ClassLoader, LogFactory>>)Class.forName(storeImplementationClass);
         result = implementationClass.getConstructor().newInstance();
      } catch (Throwable t) {
         handleThrowable(t);
         if (!"org.apache.commons.logging.impl.WeakHashtable".equals(storeImplementationClass)) {
            if (isDiagnosticsEnabled()) {
               logDiagnostic("[ERROR] LogFactory: Load of custom Hashtable failed");
            } else {
               System.err.println("[ERROR] LogFactory: Load of custom Hashtable failed");
            }
         }
      }

      if (result == null) {
         result = new Hashtable<>();
      }

      return result;
   }

   protected static ClassLoader directGetContextClassLoader() throws LogConfigurationException {
      ClassLoader classLoader = null;

      try {
         classLoader = Thread.currentThread().getContextClassLoader();
      } catch (SecurityException var2) {
      }

      return classLoader;
   }

   private static LogFactory getCachedFactory(ClassLoader contextClassLoader) {
      return contextClassLoader == null ? nullClassLoaderFactory : factories.get(contextClassLoader);
   }

   protected static ClassLoader getClassLoader(Class<?> clazz) {
      try {
         return clazz.getClassLoader();
      } catch (SecurityException ex) {
         logDiagnostic(() -> "Unable to get class loader for class '" + clazz + "' due to security restrictions - " + ex.getMessage());
         throw ex;
      }
   }

   private static Properties getConfigurationFile(ClassLoader classLoader, String fileName) {
      Properties props = null;
      double priority = 0.0;
      URL propsUrl = null;

      try {
         Enumeration<URL> urls = getResources(classLoader, fileName);
         if (urls == null) {
            return null;
         }

         while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            Properties newProps = getProperties(url);
            if (newProps != null) {
               if (props == null) {
                  propsUrl = url;
                  props = newProps;
                  String priorityStr = props.getProperty("priority");
                  priority = 0.0;
                  if (priorityStr != null) {
                     priority = Double.parseDouble(priorityStr);
                  }

                  if (isDiagnosticsEnabled()) {
                     logDiagnostic("[LOOKUP] Properties file found at '" + url + "' with priority " + priority);
                  }
               } else {
                  String newPriorityStr = newProps.getProperty("priority");
                  double newPriority = 0.0;
                  if (newPriorityStr != null) {
                     newPriority = Double.parseDouble(newPriorityStr);
                  }

                  if (newPriority > priority) {
                     if (isDiagnosticsEnabled()) {
                        logDiagnostic(
                           "[LOOKUP] Properties file at '"
                              + url
                              + "' with priority "
                              + newPriority
                              + " overrides file at '"
                              + propsUrl
                              + "' with priority "
                              + priority
                        );
                     }

                     propsUrl = url;
                     props = newProps;
                     priority = newPriority;
                  } else if (isDiagnosticsEnabled()) {
                     logDiagnostic(
                        "[LOOKUP] Properties file at '"
                           + url
                           + "' with priority "
                           + newPriority
                           + " does not override file at '"
                           + propsUrl
                           + "' with priority "
                           + priority
                     );
                  }
               }
            }
         }
      } catch (SecurityException e) {
         logDiagnostic("SecurityException thrown while trying to find/read config files.");
      }

      if (isDiagnosticsEnabled()) {
         if (props == null) {
            logDiagnostic("[LOOKUP] No properties file of name '" + fileName + "' found.");
         } else {
            logDiagnostic("[LOOKUP] Properties file of name '" + fileName + "' found at '" + propsUrl + '"');
         }
      }

      return props;
   }

   protected static ClassLoader getContextClassLoader() throws LogConfigurationException {
      return directGetContextClassLoader();
   }

   private static ClassLoader getContextClassLoaderInternal() throws LogConfigurationException {
      return AccessController.doPrivileged(LogFactory::directGetContextClassLoader);
   }

   public static LogFactory getFactory() throws LogConfigurationException {
      ClassLoader contextClassLoader = getContextClassLoaderInternal();
      if (contextClassLoader == null) {
         logDiagnostic("Context class loader is null.");
      }

      LogFactory factory = getCachedFactory(contextClassLoader);
      if (factory != null) {
         return factory;
      }

      if (isDiagnosticsEnabled()) {
         logDiagnostic("[LOOKUP] LogFactory implementation requested for the first time for context class loader " + objectId(contextClassLoader));
         logHierarchy("[LOOKUP] ", contextClassLoader);
      }

      Properties props = getConfigurationFile(contextClassLoader, "commons-logging.properties");
      boolean useTccl = contextClassLoader != null;
      if (props != null) {
         String useTCCLStr = props.getProperty("use_tccl");
         useTccl &= useTCCLStr == null || Boolean.parseBoolean(useTCCLStr);
      }

      if (useTccl) {
         try {
            if (!LogFactory.class.equals(Class.forName(LogFactory.class.getName(), false, contextClassLoader))) {
               logDiagnostic(
                  () -> "The class "
                     + LogFactory.class.getName()
                     + " loaded by the context class loader "
                     + objectId(contextClassLoader)
                     + " and this class differ. Disabling the usage of the context class loader.Background can be found in https://commons.apache.org/logging/tech.html. "
               );
               logHierarchy("[BAD CL TREE] ", contextClassLoader);
               useTccl = false;
            }
         } catch (ClassNotFoundException ignored) {
            logDiagnostic(
               () -> "The class "
                  + LogFactory.class.getName()
                  + " is not present in the the context class loader "
                  + objectId(contextClassLoader)
                  + ". Disabling the usage of the context class loader.Background can be found in https://commons.apache.org/logging/tech.html. "
            );
            logHierarchy("[BAD CL TREE] ", contextClassLoader);
            useTccl = false;
         }
      }

      ClassLoader baseClassLoader = useTccl ? contextClassLoader : thisClassLoaderRef.get();
      logDiagnostic(() -> "[LOOKUP] Looking for system property [org.apache.commons.logging.LogFactory] to define the LogFactory subclass to use...");

      try {
         String factoryClass = getSystemProperty("org.apache.commons.logging.LogFactory", null);
         if (factoryClass != null) {
            logDiagnostic(
               () -> "[LOOKUP] Creating an instance of LogFactory class '"
                  + factoryClass
                  + "' as specified by system property "
                  + "org.apache.commons.logging.LogFactory"
            );
            factory = newFactory(factoryClass, baseClassLoader, contextClassLoader);
         } else {
            logDiagnostic(() -> "[LOOKUP] No system property [org.apache.commons.logging.LogFactory] defined.");
         }
      } catch (SecurityException e) {
         logDiagnostic(
            () -> "[LOOKUP] A security exception occurred while trying to create an instance of the custom factory class: ["
               + trim(e.getMessage())
               + "]. Trying alternative implementations..."
         );
      } catch (RuntimeException e) {
         logDiagnostic(
            () -> "[LOOKUP] An exception occurred while trying to create an instance of the custom factory class: ["
               + trim(e.getMessage())
               + "] as specified by a system property."
         );
         throw e;
      }

      if (factory == null) {
         logDiagnostic("[LOOKUP] Using ServiceLoader  to define the LogFactory subclass to use...");

         try {
            ServiceLoader<LogFactory> serviceLoader = ServiceLoader.load(LogFactory.class, baseClassLoader);
            Iterator<LogFactory> iterator = serviceLoader.iterator();
            int i = 3;

            while (factory == null && i-- > 0) {
               try {
                  if (iterator.hasNext()) {
                     factory = iterator.next();
                  }
               } catch (ServiceConfigurationError | LinkageError ex) {
                  logDiagnostic(
                     () -> "[LOOKUP] An exception occurred while trying to find an instance of LogFactory: ["
                        + trim(ex.getMessage())
                        + "]. Trying alternative implementations..."
                  );
               }
            }
         } catch (Exception ex) {
            logDiagnostic(
               () -> "[LOOKUP] A security exception occurred while trying to create an instance of the custom factory class: ["
                  + trim(ex.getMessage())
                  + "]. Trying alternative implementations..."
            );
         }
      }

      if (factory == null) {
         if (props != null) {
            logDiagnostic(
               () -> "[LOOKUP] Looking in properties file for entry with key 'org.apache.commons.logging.LogFactory' to define the LogFactory subclass to use..."
            );
            String factoryClass = props.getProperty("org.apache.commons.logging.LogFactory");
            if (factoryClass != null) {
               logDiagnostic(() -> "[LOOKUP] Properties file specifies LogFactory subclass '" + factoryClass + "'");
               factory = newFactory(factoryClass, baseClassLoader, contextClassLoader);
            } else {
               logDiagnostic("[LOOKUP] Properties file has no entry specifying LogFactory subclass.");
            }
         } else {
            logDiagnostic("[LOOKUP] No properties file available to determine LogFactory subclass from..");
         }
      }

      if (factory == null) {
         factory = newStandardFactory(baseClassLoader);
      }

      if (factory == null && baseClassLoader != thisClassLoaderRef.get()) {
         factory = newStandardFactory(thisClassLoaderRef.get());
      }

      if (factory != null) {
         if (isDiagnosticsEnabled()) {
            logDiagnostic("Created object " + objectId(factory) + " to manage class loader " + objectId(contextClassLoader));
         }
      } else {
         logDiagnostic(
            () -> "[LOOKUP] Loading the default LogFactory implementation 'org.apache.commons.logging.impl.LogFactoryImpl' via the same class loader that loaded this LogFactory class (ie not looking in the context class loader)."
         );
         factory = newFactory("org.apache.commons.logging.impl.LogFactoryImpl", thisClassLoaderRef.get(), contextClassLoader);
      }

      if (factory != null) {
         cacheFactory(contextClassLoader, factory);
         if (props != null) {
            Enumeration<?> names = props.propertyNames();

            while (names.hasMoreElements()) {
               String name = Objects.toString(names.nextElement(), null);
               String value = props.getProperty(name);
               factory.setAttribute(name, value);
            }
         }
      }

      return factory;
   }

   public static Log getLog(Class<?> clazz) throws LogConfigurationException {
      return getFactory().getInstance(clazz);
   }

   public static Log getLog(String name) throws LogConfigurationException {
      return getFactory().getInstance(name);
   }

   private static Properties getProperties(URL url) {
      return AccessController.doPrivileged(() -> {
         try {
            URLConnection connection = url.openConnection();
            connection.setUseCaches(false);

            try {
               InputStream stream = connection.getInputStream();

               Properties var4;
               label56: {
                  try {
                     if (stream != null) {
                        Properties props = new Properties();
                        props.load(stream);
                        var4 = props;
                        break label56;
                     }
                  } catch (Throwable var6) {
                     if (stream != null) {
                        try {
                           stream.close();
                        } catch (Throwable var5) {
                           var6.addSuppressed(var5);
                        }
                     }

                     throw var6;
                  }

                  if (stream != null) {
                     stream.close();
                  }

                  return null;
               }

               if (stream != null) {
                  stream.close();
               }

               return var4;
            } catch (IOException e) {
               logDiagnostic(() -> "Unable to close stream for URL " + url);
            }
         } catch (IOException e) {
            logDiagnostic(() -> "Unable to read URL " + url);
         }

         return null;
      });
   }

   private static Enumeration<URL> getResources(ClassLoader loader, String name) {
      return AccessController.doPrivileged(() -> {
         try {
            return loader != null ? loader.getResources(name) : ClassLoader.getSystemResources(name);
         } catch (IOException e) {
            logDiagnostic(() -> "Exception while trying to find configuration file " + name + ":" + e.getMessage());
            return null;
         } catch (NoSuchMethodError e) {
            return null;
         }
      });
   }

   private static String getSystemProperty(String key, String def) throws SecurityException {
      return AccessController.doPrivileged(() -> System.getProperty(key, def));
   }

   protected static void handleThrowable(Throwable t) {
      if (t instanceof ThreadDeath) {
         throw (ThreadDeath)t;
      }

      if (t instanceof VirtualMachineError) {
         throw (VirtualMachineError)t;
      }
   }

   private static boolean implementsLogFactory(Class<?> logFactoryClass) {
      boolean implementsLogFactory = false;
      if (logFactoryClass != null) {
         try {
            ClassLoader logFactoryClassLoader = logFactoryClass.getClassLoader();
            if (logFactoryClassLoader == null) {
               logDiagnostic("[CUSTOM LOG FACTORY] was loaded by the boot class loader");
            } else {
               logHierarchy("[CUSTOM LOG FACTORY] ", logFactoryClassLoader);
               Class<?> factoryFromCustomLoader = Class.forName("org.apache.commons.logging.LogFactory", false, logFactoryClassLoader);
               implementsLogFactory = factoryFromCustomLoader.isAssignableFrom(logFactoryClass);
               String logFactoryClassName = logFactoryClass.getName();
               if (implementsLogFactory) {
                  logDiagnostic(() -> "[CUSTOM LOG FACTORY] " + logFactoryClassName + " implements LogFactory but was loaded by an incompatible class loader.");
               } else {
                  logDiagnostic(() -> "[CUSTOM LOG FACTORY] " + logFactoryClassName + " does not implement LogFactory.");
               }
            }
         } catch (SecurityException e) {
            logDiagnostic(
               () -> "[CUSTOM LOG FACTORY] SecurityException caught trying to determine whether the compatibility was caused by a class loader conflict: "
                  + e.getMessage()
            );
         } catch (LinkageError e) {
            logDiagnostic(
               () -> "[CUSTOM LOG FACTORY] LinkageError caught trying to determine whether the compatibility was caused by a class loader conflict: "
                  + e.getMessage()
            );
         } catch (ClassNotFoundException e) {
            logDiagnostic(
               () -> "[CUSTOM LOG FACTORY] LogFactory class cannot be loaded by the class loader which loaded the custom LogFactory implementation. Is the custom factory in the right class loader?"
            );
         }
      }

      return implementsLogFactory;
   }

   private static PrintStream initDiagnostics() {
      String dest;
      try {
         dest = getSystemProperty("org.apache.commons.logging.diagnostics.dest", null);
         if (dest == null) {
            return null;
         }
      } catch (SecurityException ex) {
         return null;
      }

      if (dest.equals("STDOUT")) {
         return System.out;
      }

      if (dest.equals("STDERR")) {
         return System.err;
      }

      try {
         FileOutputStream fos = new FileOutputStream(dest, true);
         return new PrintStream(fos, false, StandardCharsets.UTF_8.name());
      } catch (IOException ex) {
         return null;
      }
   }

   private static boolean isClassAvailable(String className, ClassLoader classLoader) {
      logDiagnostic(() -> "Checking if class '" + className + "' is available in class loader " + objectId(classLoader));

      try {
         Class.forName(className, true, classLoader);
         return true;
      } catch (ClassNotFoundException | LinkageError e) {
         logDiagnostic(() -> "Failed to load class '" + className + "' from class loader " + objectId(classLoader) + ": " + e.getMessage());
         return false;
      }
   }

   protected static boolean isDiagnosticsEnabled() {
      return DIAGNOSTICS_STREAM != null;
   }

   private static void logClassLoaderEnvironment(Class<?> clazz) {
      if (isDiagnosticsEnabled()) {
         try {
            logDiagnostic("[ENV] Extension directories (java.ext.dir): " + System.getProperty("java.ext.dir"));
            logDiagnostic("[ENV] Application classpath (java.class.path): " + System.getProperty("java.class.path"));
         } catch (SecurityException ex) {
            logDiagnostic("[ENV] Security setting prevent interrogation of system classpaths.");
         }

         String className = clazz.getName();

         ClassLoader classLoader;
         try {
            classLoader = getClassLoader(clazz);
         } catch (SecurityException ex) {
            logDiagnostic("[ENV] Security forbids determining the class loader for " + className);
            return;
         }

         logDiagnostic("[ENV] Class " + className + " was loaded via class loader " + objectId(classLoader));
         logHierarchy("[ENV] Ancestry of class loader which loaded " + className + " is ", classLoader);
      }
   }

   private static void logDiagnostic(String msg) {
      if (DIAGNOSTICS_STREAM != null) {
         logDiagnosticDirect(msg);
      }
   }

   private static void logDiagnostic(Supplier<String> msg) {
      if (DIAGNOSTICS_STREAM != null) {
         logDiagnosticDirect(msg.get());
      }
   }

   private static void logDiagnosticDirect(String msg) {
      DIAGNOSTICS_STREAM.print(DIAGNOSTICS_PREFIX);
      DIAGNOSTICS_STREAM.println(msg);
      DIAGNOSTICS_STREAM.flush();
   }

   private static void logHierarchy(String prefix, ClassLoader classLoader) {
      if (isDiagnosticsEnabled()) {
         if (classLoader != null) {
            logDiagnostic(prefix + objectId(classLoader) + " == '" + classLoader.toString() + "'");
         }

         ClassLoader systemClassLoader;
         try {
            systemClassLoader = ClassLoader.getSystemClassLoader();
         } catch (SecurityException ex) {
            logDiagnostic(prefix + "Security forbids determining the system class loader.");
            return;
         }

         if (classLoader != null) {
            StringBuilder buf = new StringBuilder(prefix + "ClassLoader tree:");

            while (true) {
               buf.append(objectId(classLoader));
               if (classLoader == systemClassLoader) {
                  buf.append(" (SYSTEM) ");
               }

               try {
                  classLoader = classLoader.getParent();
               } catch (SecurityException ex) {
                  buf.append(" --> SECRET");
                  break;
               }

               buf.append(" --> ");
               if (classLoader == null) {
                  buf.append("BOOT");
                  break;
               }
            }

            logDiagnostic(buf.toString());
         }
      }
   }

   protected static final void logRawDiagnostic(String msg) {
      if (DIAGNOSTICS_STREAM != null) {
         DIAGNOSTICS_STREAM.println(msg);
         DIAGNOSTICS_STREAM.flush();
      }
   }

   protected static LogFactory newFactory(String factoryClass, ClassLoader classLoader) {
      return newFactory(factoryClass, classLoader, null);
   }

   protected static LogFactory newFactory(String factoryClass, ClassLoader classLoader, ClassLoader contextClassLoader) throws LogConfigurationException {
      Object result = AccessController.doPrivileged(() -> createFactory(factoryClass, classLoader));
      if (result instanceof LogConfigurationException) {
         LogConfigurationException ex = (LogConfigurationException)result;
         logDiagnostic(() -> "An error occurred while loading the factory class:" + ex.getMessage());
         throw ex;
      } else {
         logDiagnostic(() -> "Created object " + objectId(result) + " to manage class loader " + objectId(contextClassLoader));
         return (LogFactory)result;
      }
   }

   private static LogFactory newStandardFactory(ClassLoader classLoader) {
      if (isClassAvailable("org.apache.logging.slf4j.SLF4JProvider", classLoader)) {
         try {
            return (LogFactory)Class.forName("org.apache.commons.logging.impl.Slf4jLogFactory", true, classLoader).getConstructor().newInstance();
         } catch (LinkageError | ReflectiveOperationException var44) {
         } finally {
            logDiagnostic(
               () -> "[LOOKUP] Log4j API to SLF4J redirection detected. Loading the SLF4J LogFactory implementation 'org.apache.commons.logging.impl.Slf4jLogFactory'."
            );
         }
      }

      try {
         return (LogFactory)Class.forName("org.apache.commons.logging.impl.Log4jApiLogFactory", true, classLoader).getConstructor().newInstance();
      } catch (LinkageError | ReflectiveOperationException var42) {
      } finally {
         logDiagnostic(() -> "[LOOKUP] Loading the Log4j API LogFactory implementation 'org.apache.commons.logging.impl.Log4jApiLogFactory'.");
      }

      try {
         return (LogFactory)Class.forName("org.apache.commons.logging.impl.Slf4jLogFactory", true, classLoader).getConstructor().newInstance();
      } catch (LinkageError | ReflectiveOperationException var40) {
      } finally {
         logDiagnostic(() -> "[LOOKUP] Loading the SLF4J LogFactory implementation 'org.apache.commons.logging.impl.Slf4jLogFactory'.");
      }

      try {
         return (LogFactory)Class.forName("org.apache.commons.logging.impl.LogFactoryImpl", true, classLoader).getConstructor().newInstance();
      } catch (LinkageError | ReflectiveOperationException var38) {
      } finally {
         logDiagnostic(() -> "[LOOKUP] Loading the legacy LogFactory implementation 'org.apache.commons.logging.impl.LogFactoryImpl'.");
      }

      return null;
   }

   public static String objectId(Object obj) {
      return obj == null ? "null" : obj.getClass().getName() + "@" + System.identityHashCode(obj);
   }

   public static void release(ClassLoader classLoader) {
      logDiagnostic(() -> "Releasing factory for class loader " + objectId(classLoader));
      Hashtable<ClassLoader, LogFactory> factories = LogFactory.factories;
      synchronized (factories) {
         if (classLoader == null) {
            if (nullClassLoaderFactory != null) {
               nullClassLoaderFactory.release();
               nullClassLoaderFactory = null;
            }
         } else {
            LogFactory factory = factories.get(classLoader);
            if (factory != null) {
               factory.release();
               factories.remove(classLoader);
            }
         }
      }
   }

   public static void releaseAll() {
      logDiagnostic("Releasing factory for all class loaders.");
      Hashtable<ClassLoader, LogFactory> factories = LogFactory.factories;
      synchronized (factories) {
         factories.values().forEach(LogFactory::release);
         factories.clear();
         if (nullClassLoaderFactory != null) {
            nullClassLoaderFactory.release();
            nullClassLoaderFactory = null;
         }
      }
   }

   private static String trim(String src) {
      return src != null ? src.trim() : null;
   }

   protected LogFactory() {
   }

   public abstract Object getAttribute(String var1);

   public abstract String[] getAttributeNames();

   public abstract Log getInstance(Class<?> var1) throws LogConfigurationException;

   public abstract Log getInstance(String var1) throws LogConfigurationException;

   public abstract void release();

   public abstract void removeAttribute(String var1);

   public abstract void setAttribute(String var1, Object var2);

   static {
      ClassLoader thisClassLoader = getClassLoader(LogFactory.class);
      thisClassLoaderRef = new WeakReference<>(thisClassLoader);

      String classLoaderName;
      try {
         classLoaderName = thisClassLoader != null ? objectId(thisClassLoader) : "BOOTLOADER";
      } catch (SecurityException e) {
         classLoaderName = "UNKNOWN";
      }

      DIAGNOSTICS_PREFIX = "[LogFactory from " + classLoaderName + "] ";
      DIAGNOSTICS_STREAM = initDiagnostics();
      logClassLoaderEnvironment(LogFactory.class);
      factories = createFactoryStore();
      logDiagnostic("BOOTSTRAP COMPLETED");
   }
}
