package org.springframework.context.support;

import groovy.lang.GroovyObject;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.groovy.GroovyBeanDefinitionReader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

public class GenericGroovyApplicationContext extends GenericApplicationContext implements GroovyObject {
   private final GroovyBeanDefinitionReader reader = new GroovyBeanDefinitionReader(this);
   private final BeanWrapper contextWrapper = new BeanWrapperImpl(this);
   private MetaClass metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(this.getClass());

   public GenericGroovyApplicationContext() {
   }

   public GenericGroovyApplicationContext(Resource... resources) {
      this.load(resources);
      this.refresh();
   }

   public GenericGroovyApplicationContext(String... resourceLocations) {
      this.load(resourceLocations);
      this.refresh();
   }

   public GenericGroovyApplicationContext(Class<?> relativeClass, String... resourceNames) {
      this.load(relativeClass, resourceNames);
      this.refresh();
   }

   public final GroovyBeanDefinitionReader getReader() {
      return this.reader;
   }

   @Override
   public void setEnvironment(ConfigurableEnvironment environment) {
      super.setEnvironment(environment);
      this.reader.setEnvironment(this.getEnvironment());
   }

   public void load(Resource... resources) {
      this.reader.loadBeanDefinitions(resources);
   }

   public void load(String... resourceLocations) {
      this.reader.loadBeanDefinitions(resourceLocations);
   }

   public void load(Class<?> relativeClass, String... resourceNames) {
      Resource[] resources = new Resource[resourceNames.length];

      for (int i = 0; i < resourceNames.length; i++) {
         resources[i] = new ClassPathResource(resourceNames[i], relativeClass);
      }

      this.load(resources);
   }

   public void setMetaClass(MetaClass metaClass) {
      this.metaClass = metaClass;
   }

   public MetaClass getMetaClass() {
      return this.metaClass;
   }

   public Object invokeMethod(String name, Object args) {
      return this.metaClass.invokeMethod(this, name, args);
   }

   public void setProperty(String property, Object newValue) {
      if (newValue instanceof BeanDefinition beanDefinition) {
         this.registerBeanDefinition(property, beanDefinition);
      } else {
         this.metaClass.setProperty(this, property, newValue);
      }
   }

   public @Nullable Object getProperty(String property) {
      if (this.containsBean(property)) {
         return this.getBean(property);
      } else if (this.contextWrapper.isReadableProperty(property)) {
         return this.contextWrapper.getPropertyValue(property);
      } else {
         throw new NoSuchBeanDefinitionException(property);
      }
   }
}
