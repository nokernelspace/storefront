package org.springframework.context;

import java.io.Closeable;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ProtocolResolver;
import org.springframework.core.metrics.ApplicationStartup;

public interface ConfigurableApplicationContext extends ApplicationContext, Lifecycle, Closeable {
   String CONFIG_LOCATION_DELIMITERS = ",; \t\n";
   String BOOTSTRAP_EXECUTOR_BEAN_NAME = "bootstrapExecutor";
   String CONVERSION_SERVICE_BEAN_NAME = "conversionService";
   String LOAD_TIME_WEAVER_BEAN_NAME = "loadTimeWeaver";
   String ENVIRONMENT_BEAN_NAME = "environment";
   String SYSTEM_PROPERTIES_BEAN_NAME = "systemProperties";
   String SYSTEM_ENVIRONMENT_BEAN_NAME = "systemEnvironment";
   String APPLICATION_STARTUP_BEAN_NAME = "applicationStartup";
   String SHUTDOWN_HOOK_THREAD_NAME = "SpringContextShutdownHook";

   void setId(String id);

   void setParent(@Nullable ApplicationContext parent);

   void setEnvironment(ConfigurableEnvironment environment);

   ConfigurableEnvironment getEnvironment();

   void setApplicationStartup(ApplicationStartup applicationStartup);

   ApplicationStartup getApplicationStartup();

   void addBeanFactoryPostProcessor(BeanFactoryPostProcessor postProcessor);

   void addApplicationListener(ApplicationListener<?> listener);

   void removeApplicationListener(ApplicationListener<?> listener);

   void setClassLoader(ClassLoader classLoader);

   void addProtocolResolver(ProtocolResolver resolver);

   void refresh() throws BeansException, IllegalStateException;

   void restart();

   void pause();

   void registerShutdownHook();

   @Override
   void close();

   boolean isClosed();

   boolean isActive();

   ConfigurableListableBeanFactory getBeanFactory() throws IllegalStateException;
}
