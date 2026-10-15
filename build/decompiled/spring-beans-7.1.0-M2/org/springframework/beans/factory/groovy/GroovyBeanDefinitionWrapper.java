package org.springframework.beans.factory.groovy;

import groovy.lang.GroovyObjectSupport;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

class GroovyBeanDefinitionWrapper extends GroovyObjectSupport {
   private static final String PARENT = "parent";
   private static final String AUTOWIRE = "autowire";
   private static final String CONSTRUCTOR_ARGS = "constructorArgs";
   private static final String FACTORY_BEAN = "factoryBean";
   private static final String FACTORY_METHOD = "factoryMethod";
   private static final String INIT_METHOD = "initMethod";
   private static final String DESTROY_METHOD = "destroyMethod";
   private static final String SINGLETON = "singleton";
   private static final Set<String> dynamicProperties = Set.of(
      "parent", "autowire", "constructorArgs", "factoryBean", "factoryMethod", "initMethod", "destroyMethod", "singleton"
   );
   private @Nullable String beanName;
   private final @Nullable Class<?> clazz;
   private final @Nullable Collection<?> constructorArgs;
   private @Nullable AbstractBeanDefinition definition;
   private @Nullable BeanWrapper definitionWrapper;
   private @Nullable String parentName;

   GroovyBeanDefinitionWrapper(String beanName) {
      this(beanName, null);
   }

   GroovyBeanDefinitionWrapper(@Nullable String beanName, @Nullable Class<?> clazz) {
      this(beanName, clazz, null);
   }

   GroovyBeanDefinitionWrapper(@Nullable String beanName, @Nullable Class<?> clazz, @Nullable Collection<?> constructorArgs) {
      this.beanName = beanName;
      this.clazz = clazz;
      this.constructorArgs = constructorArgs;
   }

   public @Nullable String getBeanName() {
      return this.beanName;
   }

   void setBeanDefinition(AbstractBeanDefinition definition) {
      this.definition = definition;
   }

   AbstractBeanDefinition getBeanDefinition() {
      if (this.definition == null) {
         this.definition = this.createBeanDefinition();
      }

      return this.definition;
   }

   protected AbstractBeanDefinition createBeanDefinition() {
      AbstractBeanDefinition bd = new GenericBeanDefinition();
      bd.setBeanClass(this.clazz);
      if (!CollectionUtils.isEmpty(this.constructorArgs)) {
         ConstructorArgumentValues cav = new ConstructorArgumentValues();

         for (Object constructorArg : this.constructorArgs) {
            cav.addGenericArgumentValue(constructorArg);
         }

         bd.setConstructorArgumentValues(cav);
      }

      if (this.parentName != null) {
         bd.setParentName(this.parentName);
      }

      this.definitionWrapper = new BeanWrapperImpl(bd);
      return bd;
   }

   void setBeanDefinitionHolder(BeanDefinitionHolder holder) {
      this.definition = (AbstractBeanDefinition)holder.getBeanDefinition();
      this.beanName = holder.getBeanName();
   }

   BeanDefinitionHolder getBeanDefinitionHolder() {
      Assert.state(this.beanName != null, "Bean name must be set");
      return new BeanDefinitionHolder(this.getBeanDefinition(), this.beanName);
   }

   void setParent(@Nullable Object obj) {
      Assert.notNull(obj, "Parent bean cannot be set to a null runtime bean reference");
      if (obj instanceof String name) {
         this.parentName = name;
      } else if (obj instanceof RuntimeBeanReference runtimeBeanReference) {
         this.parentName = runtimeBeanReference.getBeanName();
      } else if (obj instanceof GroovyBeanDefinitionWrapper wrapper) {
         this.parentName = wrapper.getBeanName();
      }

      this.getBeanDefinition().setParentName(this.parentName);
      this.getBeanDefinition().setAbstract(false);
   }

   GroovyBeanDefinitionWrapper addProperty(String propertyName, @Nullable Object propertyValue) {
      if (propertyValue instanceof GroovyBeanDefinitionWrapper wrapper) {
         propertyValue = wrapper.getBeanDefinition();
      }

      this.getBeanDefinition().getPropertyValues().add(propertyName, propertyValue);
      return this;
   }

   public @Nullable Object getProperty(String property) {
      Assert.state(this.definitionWrapper != null, "BeanDefinition wrapper not initialized");
      if (this.definitionWrapper.isReadableProperty(property)) {
         return this.definitionWrapper.getPropertyValue(property);
      } else {
         return dynamicProperties.contains(property) ? null : super.getProperty(property);
      }
   }

   public void setProperty(String property, @Nullable Object newValue) {
      if ("parent".equals(property)) {
         this.setParent(newValue);
      } else {
         AbstractBeanDefinition bd = this.getBeanDefinition();
         Assert.state(this.definitionWrapper != null, "BeanDefinition wrapper not initialized");
         if ("autowire".equals(property)) {
            if ("byName".equals(newValue)) {
               bd.setAutowireMode(1);
            } else if ("byType".equals(newValue)) {
               bd.setAutowireMode(2);
            } else if ("constructor".equals(newValue)) {
               bd.setAutowireMode(3);
            } else if (Boolean.TRUE.equals(newValue)) {
               bd.setAutowireMode(1);
            }
         } else if ("constructorArgs".equals(property) && newValue instanceof List<?> args) {
            ConstructorArgumentValues cav = new ConstructorArgumentValues();

            for (Object arg : args) {
               cav.addGenericArgumentValue(arg);
            }

            bd.setConstructorArgumentValues(cav);
         } else if ("factoryBean".equals(property)) {
            if (newValue != null) {
               bd.setFactoryBeanName(newValue.toString());
            }
         } else if ("factoryMethod".equals(property)) {
            if (newValue != null) {
               bd.setFactoryMethodName(newValue.toString());
            }
         } else if ("initMethod".equals(property)) {
            if (newValue != null) {
               bd.setInitMethodName(newValue.toString());
            }
         } else if ("destroyMethod".equals(property)) {
            if (newValue != null) {
               bd.setDestroyMethodName(newValue.toString());
            }
         } else if ("singleton".equals(property)) {
            bd.setScope(Boolean.TRUE.equals(newValue) ? "singleton" : "prototype");
         } else if (this.definitionWrapper.isWritableProperty(property)) {
            this.definitionWrapper.setPropertyValue(property, newValue);
         } else {
            super.setProperty(property, newValue);
         }
      }
   }
}
