package org.springframework.web.context;

import jakarta.servlet.ServletContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public class ContextLoader {
   public static final String CONTEXT_ID_PARAM = "contextId";
   public static final String CONFIG_LOCATION_PARAM = "contextConfigLocation";
   public static final String CONTEXT_CLASS_PARAM = "contextClass";
   public static final String CONTEXT_INITIALIZER_CLASSES_PARAM = "contextInitializerClasses";
   public static final String GLOBAL_INITIALIZER_CLASSES_PARAM = "globalInitializerClasses";
   private static final String INIT_PARAM_DELIMITERS = ",; \t\n";
   private static final String DEFAULT_STRATEGIES_PATH = "ContextLoader.properties";
   private static final Map<ClassLoader, WebApplicationContext> currentContextPerThread = new ConcurrentHashMap<>(1);
   private static volatile @Nullable WebApplicationContext currentContext;
   private static @Nullable Properties defaultStrategies;
   private @Nullable WebApplicationContext rootContext;
   private final List<ApplicationContextInitializer<ConfigurableApplicationContext>> contextInitializers = new ArrayList<>();

   public ContextLoader() {
   }

   public ContextLoader(WebApplicationContext rootContext) {
      this.rootContext = rootContext;
   }

   public void setContextInitializers(ApplicationContextInitializer<?> @Nullable ... initializers) {
      if (initializers != null) {
         for (ApplicationContextInitializer<?> initializer : initializers) {
            this.contextInitializers.add((ApplicationContextInitializer<ConfigurableApplicationContext>)initializer);
         }
      }
   }

   public WebApplicationContext initWebApplicationContext(ServletContext servletContext) {
      if (servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE) != null) {
         throw new IllegalStateException(
            "Cannot initialize context because there is already a root application context present - check whether you have multiple ContextLoader* definitions in your web.xml!"
         );
      }

      servletContext.log("Initializing Spring root WebApplicationContext");
      Log logger = LogFactory.getLog(ContextLoader.class);
      if (logger.isInfoEnabled()) {
         logger.info("Root WebApplicationContext: initialization started");
      }

      long startTime = System.currentTimeMillis();

      try {
         if (this.rootContext == null) {
            this.rootContext = this.createWebApplicationContext(servletContext);
         }

         if (this.rootContext instanceof ConfigurableWebApplicationContext cwac && !cwac.isActive()) {
            if (cwac.getParent() == null) {
               ApplicationContext parent = this.loadParentContext(servletContext);
               cwac.setParent(parent);
            }

            this.configureAndRefreshWebApplicationContext(cwac, servletContext);
         }

         servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, this.rootContext);
         ClassLoader ccl = Thread.currentThread().getContextClassLoader();
         if (ccl == ContextLoader.class.getClassLoader()) {
            currentContext = this.rootContext;
         } else if (ccl != null) {
            currentContextPerThread.put(ccl, this.rootContext);
         }

         if (logger.isInfoEnabled()) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            logger.info("Root WebApplicationContext initialized in " + elapsedTime + " ms");
         }

         return this.rootContext;
      } catch (RuntimeException | Error ex) {
         logger.error("Context initialization failed", ex);
         servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, ex);
         throw ex;
      }
   }

   protected WebApplicationContext createWebApplicationContext(ServletContext sc) {
      Class<?> contextClass = this.determineContextClass(sc);
      if (!ConfigurableWebApplicationContext.class.isAssignableFrom(contextClass)) {
         throw new ApplicationContextException(
            "Custom context class [" + contextClass.getName() + "] is not of type [" + ConfigurableWebApplicationContext.class.getName() + "]"
         );
      } else {
         return (ConfigurableWebApplicationContext)BeanUtils.instantiateClass(contextClass);
      }
   }

   protected Class<?> determineContextClass(ServletContext servletContext) {
      String contextClassName = servletContext.getInitParameter("contextClass");
      if (contextClassName != null) {
         try {
            return ClassUtils.forName(contextClassName, ClassUtils.getDefaultClassLoader());
         } catch (ClassNotFoundException ex) {
            throw new ApplicationContextException("Failed to load custom context class [" + contextClassName + "]", ex);
         }
      } else {
         if (defaultStrategies == null) {
            try {
               ClassPathResource resource = new ClassPathResource("ContextLoader.properties", ContextLoader.class);
               defaultStrategies = PropertiesLoaderUtils.loadProperties(resource);
            } catch (IOException ex) {
               throw new IllegalStateException("Could not load 'ContextLoader.properties': " + ex.getMessage());
            }
         }

         contextClassName = defaultStrategies.getProperty(WebApplicationContext.class.getName());

         try {
            return ClassUtils.forName(contextClassName, ContextLoader.class.getClassLoader());
         } catch (ClassNotFoundException ex) {
            throw new ApplicationContextException("Failed to load default context class [" + contextClassName + "]", ex);
         }
      }
   }

   protected void configureAndRefreshWebApplicationContext(ConfigurableWebApplicationContext wac, ServletContext sc) {
      if (ObjectUtils.identityToString(wac).equals(wac.getId())) {
         String idParam = sc.getInitParameter("contextId");
         if (idParam != null) {
            wac.setId(idParam);
         } else {
            wac.setId(ConfigurableWebApplicationContext.APPLICATION_CONTEXT_ID_PREFIX + ObjectUtils.getDisplayString(sc.getContextPath()));
         }
      }

      wac.setServletContext(sc);
      String configLocationParam = sc.getInitParameter("contextConfigLocation");
      if (configLocationParam != null) {
         wac.setConfigLocation(configLocationParam);
      }

      if (wac.getEnvironment() instanceof ConfigurableWebEnvironment cwe) {
         cwe.initPropertySources(sc, null);
      }

      this.customizeContext(sc, wac);
      wac.refresh();
   }

   protected void customizeContext(ServletContext sc, ConfigurableWebApplicationContext wac) {
      for (Class<ApplicationContextInitializer<ConfigurableApplicationContext>> initializerClass : this.determineContextInitializerClasses(sc)) {
         Class<?> initializerContextClass = GenericTypeResolver.resolveTypeArgument(initializerClass, ApplicationContextInitializer.class);
         if (initializerContextClass != null && !initializerContextClass.isInstance(wac)) {
            throw new ApplicationContextException(
               String.format(
                  "Could not apply context initializer [%s] since its generic parameter [%s] is not assignable from the type of application context used by this context loader: [%s]",
                  initializerClass.getName(),
                  initializerContextClass.getName(),
                  wac.getClass().getName()
               )
            );
         }

         this.contextInitializers.add((ApplicationContextInitializer<ConfigurableApplicationContext>)BeanUtils.instantiateClass(initializerClass));
      }

      AnnotationAwareOrderComparator.sort(this.contextInitializers);

      for (ApplicationContextInitializer<ConfigurableApplicationContext> initializer : this.contextInitializers) {
         initializer.initialize(wac);
      }
   }

   protected List<Class<ApplicationContextInitializer<ConfigurableApplicationContext>>> determineContextInitializerClasses(ServletContext servletContext) {
      List<Class<ApplicationContextInitializer<ConfigurableApplicationContext>>> classes = new ArrayList<>();
      String globalClassNames = servletContext.getInitParameter("globalInitializerClasses");
      if (globalClassNames != null) {
         for (String className : StringUtils.tokenizeToStringArray(globalClassNames, ",; \t\n")) {
            classes.add(this.loadInitializerClass(className));
         }
      }

      String localClassNames = servletContext.getInitParameter("contextInitializerClasses");
      if (localClassNames != null) {
         for (String className : StringUtils.tokenizeToStringArray(localClassNames, ",; \t\n")) {
            classes.add(this.loadInitializerClass(className));
         }
      }

      return classes;
   }

   private Class<ApplicationContextInitializer<ConfigurableApplicationContext>> loadInitializerClass(String className) {
      try {
         Class<?> clazz = ClassUtils.forName(className, ClassUtils.getDefaultClassLoader());
         if (!ApplicationContextInitializer.class.isAssignableFrom(clazz)) {
            throw new ApplicationContextException("Initializer class does not implement ApplicationContextInitializer interface: " + clazz);
         } else {
            return (Class<ApplicationContextInitializer<ConfigurableApplicationContext>>)clazz;
         }
      } catch (ClassNotFoundException ex) {
         throw new ApplicationContextException("Failed to load context initializer class [" + className + "]", ex);
      }
   }

   protected @Nullable ApplicationContext loadParentContext(ServletContext servletContext) {
      return null;
   }

   public void closeWebApplicationContext(ServletContext servletContext) {
      servletContext.log("Closing Spring root WebApplicationContext");

      try {
         if (this.rootContext instanceof ConfigurableWebApplicationContext cwac) {
            cwac.close();
         }
      } finally {
         ClassLoader ccl = Thread.currentThread().getContextClassLoader();
         if (ccl == ContextLoader.class.getClassLoader()) {
            currentContext = null;
         } else if (ccl != null) {
            currentContextPerThread.remove(ccl);
         }

         servletContext.removeAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
      }
   }

   public static @Nullable WebApplicationContext getCurrentWebApplicationContext() {
      ClassLoader ccl = Thread.currentThread().getContextClassLoader();
      if (ccl != null) {
         WebApplicationContext ccpt = currentContextPerThread.get(ccl);
         if (ccpt != null) {
            return ccpt;
         }
      }

      return currentContext;
   }
}
