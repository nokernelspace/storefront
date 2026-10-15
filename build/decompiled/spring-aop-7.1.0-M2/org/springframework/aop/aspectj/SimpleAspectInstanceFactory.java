package org.springframework.aop.aspectj;

import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

public class SimpleAspectInstanceFactory implements AspectInstanceFactory {
   private final Class<?> aspectClass;

   public SimpleAspectInstanceFactory(Class<?> aspectClass) {
      Assert.notNull(aspectClass, "Aspect class must not be null");
      this.aspectClass = aspectClass;
   }

   public final Class<?> getAspectClass() {
      return this.aspectClass;
   }

   @Override
   public final Object getAspectInstance() {
      try {
         return ReflectionUtils.accessibleConstructor(this.aspectClass, new Class[0]).newInstance();
      } catch (NoSuchMethodException ex) {
         throw new AopConfigException("No default constructor on aspect class: " + this.aspectClass.getName(), ex);
      } catch (InstantiationException ex) {
         throw new AopConfigException("Unable to instantiate aspect class: " + this.aspectClass.getName(), ex);
      } catch (IllegalAccessException | InaccessibleObjectException ex) {
         throw new AopConfigException("Could not access aspect constructor: " + this.aspectClass.getName(), ex);
      } catch (InvocationTargetException ex) {
         throw new AopConfigException("Failed to invoke aspect constructor: " + this.aspectClass.getName(), ex.getTargetException());
      }
   }

   @Override
   public @Nullable ClassLoader getAspectClassLoader() {
      return this.aspectClass.getClassLoader();
   }

   public int getOrder() {
      return this.getOrderForAspectClass(this.aspectClass);
   }

   protected int getOrderForAspectClass(Class<?> aspectClass) {
      return Integer.MAX_VALUE;
   }
}
