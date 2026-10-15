package org.springframework.beans.factory.xml;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.FatalBeanException;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

public class DefaultNamespaceHandlerResolver implements NamespaceHandlerResolver {
   public static final String DEFAULT_HANDLER_MAPPINGS_LOCATION = "META-INF/spring.handlers";
   protected final Log logger = LogFactory.getLog(this.getClass());
   private final @Nullable ClassLoader classLoader;
   private final String handlerMappingsLocation;
   private volatile @Nullable Map<String, Object> handlerMappings;

   public DefaultNamespaceHandlerResolver() {
      this(null, "META-INF/spring.handlers");
   }

   public DefaultNamespaceHandlerResolver(@Nullable ClassLoader classLoader) {
      this(classLoader, "META-INF/spring.handlers");
   }

   public DefaultNamespaceHandlerResolver(@Nullable ClassLoader classLoader, String handlerMappingsLocation) {
      Assert.notNull(handlerMappingsLocation, "Handler mappings location must not be null");
      this.classLoader = classLoader != null ? classLoader : ClassUtils.getDefaultClassLoader();
      this.handlerMappingsLocation = handlerMappingsLocation;
   }

   @Override
   public @Nullable NamespaceHandler resolve(String namespaceUri) {
      Map<String, Object> handlerMappings = this.getHandlerMappings();
      Object handlerOrClassName = handlerMappings.get(namespaceUri);
      if (handlerOrClassName == null) {
         return null;
      } else if (handlerOrClassName instanceof NamespaceHandler namespaceHandler) {
         return namespaceHandler;
      } else {
         String className = (String)handlerOrClassName;

         try {
            Class<?> handlerClass = ClassUtils.forName(className, this.classLoader);
            if (!NamespaceHandler.class.isAssignableFrom(handlerClass)) {
               throw new FatalBeanException(
                  "Class [" + className + "] for namespace [" + namespaceUri + "] does not implement the [" + NamespaceHandler.class.getName() + "] interface"
               );
            }

            NamespaceHandler namespaceHandler = BeanUtils.instantiateClass((Class<NamespaceHandler>)handlerClass);
            namespaceHandler.init();
            handlerMappings.put(namespaceUri, namespaceHandler);
            return namespaceHandler;
         } catch (ClassNotFoundException ex) {
            throw new FatalBeanException("Could not find NamespaceHandler class [" + className + "] for namespace [" + namespaceUri + "]", ex);
         } catch (LinkageError err) {
            throw new FatalBeanException(
               "Unresolvable class definition for NamespaceHandler class [" + className + "] for namespace [" + namespaceUri + "]", err
            );
         }
      }
   }

   private Map<String, Object> getHandlerMappings() {
      Map<String, Object> handlerMappings = this.handlerMappings;
      if (handlerMappings == null) {
         synchronized (this) {
            handlerMappings = this.handlerMappings;
            if (handlerMappings == null) {
               if (this.logger.isTraceEnabled()) {
                  this.logger.trace("Loading NamespaceHandler mappings from [" + this.handlerMappingsLocation + "]");
               }

               try {
                  Properties mappings = PropertiesLoaderUtils.loadAllProperties(this.handlerMappingsLocation, this.classLoader);
                  if (this.logger.isTraceEnabled()) {
                     this.logger.trace("Loaded NamespaceHandler mappings: " + mappings);
                  }

                  handlerMappings = new ConcurrentHashMap<>(mappings.size());
                  CollectionUtils.mergePropertiesIntoMap(mappings, handlerMappings);
                  this.handlerMappings = handlerMappings;
               } catch (IOException ex) {
                  throw new IllegalStateException("Unable to load NamespaceHandler mappings from location [" + this.handlerMappingsLocation + "]", ex);
               }
            }
         }
      }

      return handlerMappings;
   }

   @Override
   public String toString() {
      return "NamespaceHandlerResolver using mappings " + this.getHandlerMappings();
   }
}
