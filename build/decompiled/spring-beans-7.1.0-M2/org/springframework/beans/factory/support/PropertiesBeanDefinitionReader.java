package org.springframework.beans.factory.support;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Map.Entry;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.CannotLoadBeanClassException;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.util.DefaultPropertiesPersister;
import org.springframework.util.PropertiesPersister;
import org.springframework.util.StringUtils;

@Deprecated(since = "5.3")
public class PropertiesBeanDefinitionReader extends AbstractBeanDefinitionReader {
   public static final String TRUE_VALUE = "true";
   public static final String SEPARATOR = ".";
   public static final String CLASS_KEY = "(class)";
   public static final String PARENT_KEY = "(parent)";
   public static final String SCOPE_KEY = "(scope)";
   public static final String SINGLETON_KEY = "(singleton)";
   public static final String ABSTRACT_KEY = "(abstract)";
   public static final String LAZY_INIT_KEY = "(lazy-init)";
   public static final String REF_SUFFIX = "(ref)";
   public static final String REF_PREFIX = "*";
   public static final String CONSTRUCTOR_ARG_PREFIX = "$";
   private @Nullable String defaultParentBean;
   private PropertiesPersister propertiesPersister = DefaultPropertiesPersister.INSTANCE;

   public PropertiesBeanDefinitionReader(BeanDefinitionRegistry registry) {
      super(registry);
   }

   public void setDefaultParentBean(@Nullable String defaultParentBean) {
      this.defaultParentBean = defaultParentBean;
   }

   public @Nullable String getDefaultParentBean() {
      return this.defaultParentBean;
   }

   public void setPropertiesPersister(@Nullable PropertiesPersister propertiesPersister) {
      this.propertiesPersister = (PropertiesPersister)(propertiesPersister != null ? propertiesPersister : DefaultPropertiesPersister.INSTANCE);
   }

   public PropertiesPersister getPropertiesPersister() {
      return this.propertiesPersister;
   }

   @Override
   public int loadBeanDefinitions(Resource resource) throws BeanDefinitionStoreException {
      return this.loadBeanDefinitions(new EncodedResource(resource), null);
   }

   public int loadBeanDefinitions(Resource resource, @Nullable String prefix) throws BeanDefinitionStoreException {
      return this.loadBeanDefinitions(new EncodedResource(resource), prefix);
   }

   public int loadBeanDefinitions(EncodedResource encodedResource) throws BeanDefinitionStoreException {
      return this.loadBeanDefinitions(encodedResource, null);
   }

   public int loadBeanDefinitions(EncodedResource encodedResource, @Nullable String prefix) throws BeanDefinitionStoreException {
      if (this.logger.isTraceEnabled()) {
         this.logger.trace("Loading properties bean definitions from " + encodedResource);
      }

      Properties props = new Properties();

      try {
         encodedResource.getResource().consumeContent(is -> {
            if (encodedResource.getEncoding() != null) {
               this.getPropertiesPersister().load(props, new InputStreamReader(is, encodedResource.getEncoding()));
            } else {
               this.getPropertiesPersister().load(props, is);
            }
         });
         int count = this.registerBeanDefinitions(props, prefix, encodedResource.getResource().getDescription());
         if (this.logger.isDebugEnabled()) {
            this.logger.debug("Loaded " + count + " bean definitions from " + encodedResource);
         }

         return count;
      } catch (IOException ex) {
         throw new BeanDefinitionStoreException("Could not parse properties from " + encodedResource.getResource(), ex);
      }
   }

   public int registerBeanDefinitions(ResourceBundle rb) throws BeanDefinitionStoreException {
      return this.registerBeanDefinitions(rb, null);
   }

   public int registerBeanDefinitions(ResourceBundle rb, @Nullable String prefix) throws BeanDefinitionStoreException {
      Map<String, Object> map = new HashMap<>();
      Enumeration<String> keys = rb.getKeys();

      while (keys.hasMoreElements()) {
         String key = keys.nextElement();
         map.put(key, rb.getObject(key));
      }

      return this.registerBeanDefinitions(map, prefix);
   }

   public int registerBeanDefinitions(Map<?, ?> map) throws BeansException {
      return this.registerBeanDefinitions(map, null);
   }

   public int registerBeanDefinitions(Map<?, ?> map, @Nullable String prefix) throws BeansException {
      return this.registerBeanDefinitions(map, prefix, "Map " + map);
   }

   public int registerBeanDefinitions(Map<?, ?> map, @Nullable String prefix, String resourceDescription) throws BeansException {
      if (prefix == null) {
         prefix = "";
      }

      int beanCount = 0;

      for (Object key : map.keySet()) {
         if (!(key instanceof String keyString)) {
            throw new IllegalArgumentException("Illegal key [" + key + "]: only Strings allowed");
         }

         if (keyString.startsWith(prefix)) {
            String nameAndProperty = keyString.substring(prefix.length());
            int propKeyIdx = nameAndProperty.indexOf("[");
            int sepIdx;
            if (propKeyIdx != -1) {
               sepIdx = nameAndProperty.lastIndexOf(".", propKeyIdx);
            } else {
               sepIdx = nameAndProperty.lastIndexOf(".");
            }

            if (sepIdx != -1) {
               String beanName = nameAndProperty.substring(0, sepIdx);
               if (this.logger.isTraceEnabled()) {
                  this.logger.trace("Found bean name '" + beanName + "'");
               }

               if (!this.getRegistry().containsBeanDefinition(beanName)) {
                  this.registerBeanDefinition(beanName, map, prefix + beanName, resourceDescription);
                  beanCount++;
               }
            } else if (this.logger.isDebugEnabled()) {
               this.logger.debug("Invalid bean name and property [" + nameAndProperty + "]");
            }
         }
      }

      return beanCount;
   }

   protected void registerBeanDefinition(String beanName, Map<?, ?> map, String prefix, String resourceDescription) throws BeansException {
      String className = null;
      String parent = null;
      String scope = "singleton";
      boolean isAbstract = false;
      boolean lazyInit = false;
      ConstructorArgumentValues cas = new ConstructorArgumentValues();
      MutablePropertyValues pvs = new MutablePropertyValues();
      String prefixWithSep = prefix + ".";
      int beginIndex = prefixWithSep.length();

      for (Entry<?, ?> entry : map.entrySet()) {
         String key = ((String)entry.getKey()).strip();
         if (key.startsWith(prefixWithSep)) {
            String property = key.substring(beginIndex);
            if ("(class)".equals(property)) {
               className = ((String)entry.getValue()).strip();
            } else if ("(parent)".equals(property)) {
               parent = ((String)entry.getValue()).strip();
            } else if ("(abstract)".equals(property)) {
               String val = ((String)entry.getValue()).strip();
               isAbstract = "true".equals(val);
            } else if ("(scope)".equals(property)) {
               scope = ((String)entry.getValue()).strip();
            } else if (!"(singleton)".equals(property)) {
               if ("(lazy-init)".equals(property)) {
                  String val = ((String)entry.getValue()).strip();
                  lazyInit = "true".equals(val);
               } else if (property.startsWith("$")) {
                  if (property.endsWith("(ref)")) {
                     int index = Integer.parseInt(property, 1, property.length() - "(ref)".length(), 10);
                     cas.addIndexedArgumentValue(index, new RuntimeBeanReference(entry.getValue().toString()));
                  } else {
                     int index = Integer.parseInt(property, 1, property.length(), 10);
                     cas.addIndexedArgumentValue(index, this.readValue(entry));
                  }
               } else if (property.endsWith("(ref)")) {
                  property = property.substring(0, property.length() - "(ref)".length());
                  String ref = ((String)entry.getValue()).strip();
                  Object val = new RuntimeBeanReference(ref);
                  pvs.add(property, val);
               } else {
                  pvs.add(property, this.readValue(entry));
               }
            } else {
               String val = ((String)entry.getValue()).strip();
               scope = StringUtils.hasLength(val) && !"true".equals(val) ? "prototype" : "singleton";
            }
         }
      }

      if (this.logger.isTraceEnabled()) {
         this.logger.trace("Registering bean definition for bean name '" + beanName + "' with " + pvs);
      }

      if (parent == null && className == null && !beanName.equals(this.defaultParentBean)) {
         parent = this.defaultParentBean;
      }

      try {
         AbstractBeanDefinition bd = BeanDefinitionReaderUtils.createBeanDefinition(parent, className, this.getBeanClassLoader());
         bd.setScope(scope);
         bd.setAbstract(isAbstract);
         bd.setLazyInit(lazyInit);
         bd.setConstructorArgumentValues(cas);
         bd.setPropertyValues(pvs);
         this.getRegistry().registerBeanDefinition(beanName, bd);
      } catch (ClassNotFoundException ex) {
         throw new CannotLoadBeanClassException(resourceDescription, beanName, className, ex);
      } catch (LinkageError err) {
         throw new CannotLoadBeanClassException(resourceDescription, beanName, className, err);
      }
   }

   private Object readValue(Entry<?, ?> entry) {
      Object val = entry.getValue();
      if (val instanceof String strVal && strVal.startsWith("*")) {
         String targetName = strVal.substring(1);
         if (targetName.startsWith("*")) {
            val = targetName;
         } else {
            val = new RuntimeBeanReference(targetName);
         }
      }

      return val;
   }
}
