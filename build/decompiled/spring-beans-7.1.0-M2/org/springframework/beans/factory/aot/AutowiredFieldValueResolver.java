package org.springframework.beans.factory.aot;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.function.ThrowingConsumer;

public final class AutowiredFieldValueResolver extends AutowiredElementResolver {
   private final String fieldName;
   private final boolean required;
   private final @Nullable String shortcutBeanName;

   private AutowiredFieldValueResolver(String fieldName, boolean required, @Nullable String shortcut) {
      Assert.hasText(fieldName, "'fieldName' must not be empty");
      this.fieldName = fieldName;
      this.required = required;
      this.shortcutBeanName = shortcut;
   }

   public static AutowiredFieldValueResolver forField(String fieldName) {
      return new AutowiredFieldValueResolver(fieldName, false, null);
   }

   public static AutowiredFieldValueResolver forRequiredField(String fieldName) {
      return new AutowiredFieldValueResolver(fieldName, true, null);
   }

   public AutowiredFieldValueResolver withShortcut(String beanName) {
      return new AutowiredFieldValueResolver(this.fieldName, this.required, beanName);
   }

   public <T> void resolve(RegisteredBean registeredBean, ThrowingConsumer<T> action) {
      Assert.notNull(registeredBean, "'registeredBean' must not be null");
      Assert.notNull(action, "'action' must not be null");
      T resolved = this.resolve(registeredBean);
      if (resolved != null) {
         action.accept(resolved);
      }
   }

   public <T> @Nullable T resolve(RegisteredBean registeredBean, Class<T> requiredType) {
      Object value = this.resolveObject(registeredBean);
      Assert.isInstanceOf(requiredType, value);
      return (T)value;
   }

   public <T> @Nullable T resolve(RegisteredBean registeredBean) {
      return (T)this.resolveObject(registeredBean);
   }

   public @Nullable Object resolveObject(RegisteredBean registeredBean) {
      Assert.notNull(registeredBean, "'registeredBean' must not be null");
      return this.resolveValue(registeredBean, this.getField(registeredBean));
   }

   public void resolveAndSet(RegisteredBean registeredBean, Object instance) {
      Assert.notNull(registeredBean, "'registeredBean' must not be null");
      Assert.notNull(instance, "'instance' must not be null");
      Field field = this.getField(registeredBean);
      Object resolved = this.resolveValue(registeredBean, field);
      if (resolved != null) {
         ReflectionUtils.makeAccessible(field);
         ReflectionUtils.setField(field, instance, resolved);
      }
   }

   private @Nullable Object resolveValue(RegisteredBean registeredBean, Field field) {
      String beanName = registeredBean.getBeanName();
      Class<?> beanClass = registeredBean.getBeanClass();
      ConfigurableBeanFactory beanFactory = registeredBean.getBeanFactory();
      DependencyDescriptor descriptor = new DependencyDescriptor(field, this.required);
      descriptor.setContainingClass(beanClass);
      if (this.shortcutBeanName != null) {
         descriptor = new AutowiredElementResolver.ShortcutDependencyDescriptor(descriptor, this.shortcutBeanName);
      }

      Set<String> autowiredBeanNames = new LinkedHashSet<>(1);
      TypeConverter typeConverter = beanFactory.getTypeConverter();

      try {
         Assert.isInstanceOf(AutowireCapableBeanFactory.class, beanFactory);
         Object value = ((AutowireCapableBeanFactory)beanFactory).resolveDependency(descriptor, beanName, autowiredBeanNames, typeConverter);
         this.registerDependentBeans(beanFactory, beanName, autowiredBeanNames);
         return value;
      } catch (BeansException ex) {
         throw new UnsatisfiedDependencyException(null, beanName, new InjectionPoint(field), ex);
      }
   }

   private Field getField(RegisteredBean registeredBean) {
      Field field = ReflectionUtils.findField(registeredBean.getBeanClass(), this.fieldName);
      Assert.notNull(field, () -> "No field '" + this.fieldName + "' found on " + registeredBean.getBeanClass().getName());
      return field;
   }
}
