package org.springframework.beans.factory.config;

import java.lang.reflect.Field;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.FactoryBeanNotInitializedException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

public class FieldRetrievingFactoryBean implements FactoryBean<Object>, BeanNameAware, BeanClassLoaderAware, InitializingBean {
   private @Nullable Class<?> targetClass;
   private @Nullable Object targetObject;
   private @Nullable String targetField;
   private @Nullable String staticField;
   private @Nullable String beanName;
   private @Nullable ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();
   private @Nullable Field fieldObject;

   public void setTargetClass(@Nullable Class<?> targetClass) {
      this.targetClass = targetClass;
   }

   public @Nullable Class<?> getTargetClass() {
      return this.targetClass;
   }

   public void setTargetObject(@Nullable Object targetObject) {
      this.targetObject = targetObject;
   }

   public @Nullable Object getTargetObject() {
      return this.targetObject;
   }

   public void setTargetField(@Nullable String targetField) {
      this.targetField = targetField != null ? StringUtils.trimAllWhitespace(targetField) : null;
   }

   public @Nullable String getTargetField() {
      return this.targetField;
   }

   public void setStaticField(String staticField) {
      this.staticField = StringUtils.trimAllWhitespace(staticField);
   }

   @Override
   public void setBeanName(String beanName) {
      this.beanName = StringUtils.trimAllWhitespace(BeanFactoryUtils.originalBeanName(beanName));
   }

   @Override
   public void setBeanClassLoader(ClassLoader classLoader) {
      this.beanClassLoader = classLoader;
   }

   @Override
   public void afterPropertiesSet() throws ClassNotFoundException, NoSuchFieldException {
      if (this.targetClass != null && this.targetObject != null) {
         throw new IllegalArgumentException("Specify either targetClass or targetObject, not both");
      }

      if (this.targetClass == null && this.targetObject == null) {
         if (this.targetField != null) {
            throw new IllegalArgumentException("Specify targetClass or targetObject in combination with targetField");
         }

         if (this.staticField == null) {
            this.staticField = this.beanName;
            Assert.state(this.staticField != null, "No target field specified");
         }

         int lastDotIndex = this.staticField.lastIndexOf(46);
         if (lastDotIndex == -1 || lastDotIndex == this.staticField.length()) {
            throw new IllegalArgumentException(
               "staticField must be a fully qualified class plus static field name: for example, 'example.MyExampleClass.MY_EXAMPLE_FIELD'"
            );
         }

         String className = this.staticField.substring(0, lastDotIndex);
         String fieldName = this.staticField.substring(lastDotIndex + 1);
         this.targetClass = ClassUtils.forName(className, this.beanClassLoader);
         this.targetField = fieldName;
      } else if (this.targetField == null) {
         throw new IllegalArgumentException("targetField is required");
      }

      Class<?> targetClass = this.targetObject != null ? this.targetObject.getClass() : this.targetClass;
      this.fieldObject = targetClass.getField(this.targetField);
   }

   @Override
   public @Nullable Object getObject() throws IllegalAccessException {
      if (this.fieldObject == null) {
         throw new FactoryBeanNotInitializedException();
      }

      ReflectionUtils.makeAccessible(this.fieldObject);
      return this.targetObject != null ? this.fieldObject.get(this.targetObject) : this.fieldObject.get(null);
   }

   @Override
   public @Nullable Class<?> getObjectType() {
      return this.fieldObject != null ? this.fieldObject.getType() : null;
   }

   @Override
   public boolean isSingleton() {
      return false;
   }
}
