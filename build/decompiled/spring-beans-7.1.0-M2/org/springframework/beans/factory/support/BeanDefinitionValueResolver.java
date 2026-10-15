package org.springframework.beans.factory.support;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.config.NamedBeanHolder;
import org.springframework.beans.factory.config.RuntimeBeanNameReference;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public class BeanDefinitionValueResolver {
   private final AbstractAutowireCapableBeanFactory beanFactory;
   private final String beanName;
   private final BeanDefinition beanDefinition;
   private final TypeConverter typeConverter;

   public BeanDefinitionValueResolver(
      AbstractAutowireCapableBeanFactory beanFactory, String beanName, BeanDefinition beanDefinition, TypeConverter typeConverter
   ) {
      this.beanFactory = beanFactory;
      this.beanName = beanName;
      this.beanDefinition = beanDefinition;
      this.typeConverter = typeConverter;
   }

   public BeanDefinitionValueResolver(AbstractAutowireCapableBeanFactory beanFactory, String beanName, BeanDefinition beanDefinition) {
      this.beanFactory = beanFactory;
      this.beanName = beanName;
      this.beanDefinition = beanDefinition;
      BeanWrapper beanWrapper = new BeanWrapperImpl();
      beanFactory.initBeanWrapper(beanWrapper);
      this.typeConverter = beanWrapper;
   }

   public @Nullable Object resolveValueIfNecessary(Object argName, @Nullable Object value) {
      if (value instanceof RuntimeBeanReference ref) {
         return this.resolveReference(argName, ref);
      } else if (value instanceof RuntimeBeanNameReference ref) {
         String refName = ref.getBeanName();
         refName = String.valueOf(this.doEvaluate(refName));
         if (!this.beanFactory.containsBean(refName)) {
            throw new BeanDefinitionStoreException("Invalid bean name '" + refName + "' in bean reference for " + argName);
         } else {
            return refName;
         }
      } else if (value instanceof BeanDefinitionHolder bdHolder) {
         return this.resolveInnerBean(bdHolder.getBeanName(), bdHolder.getBeanDefinition(), (name, mbd) -> this.resolveInnerBeanValue(argName, name, mbd));
      } else if (value instanceof BeanDefinition bd) {
         return this.resolveInnerBean(null, bd, (name, mbd) -> this.resolveInnerBeanValue(argName, name, mbd));
      } else if (value instanceof DependencyDescriptor dependencyDescriptor) {
         Set<String> autowiredBeanNames = new LinkedHashSet<>(2);
         Object result = this.beanFactory.resolveDependency(dependencyDescriptor, this.beanName, autowiredBeanNames, this.typeConverter);

         for (String autowiredBeanName : autowiredBeanNames) {
            if (this.beanFactory.containsBean(autowiredBeanName)) {
               this.beanFactory.registerDependentBean(autowiredBeanName, this.beanName);
            }
         }

         return result;
      } else if (value instanceof ManagedArray managedArray) {
         Class<?> elementType = managedArray.resolvedElementType;
         if (elementType == null) {
            String elementTypeName = managedArray.getElementTypeName();
            if (StringUtils.hasText(elementTypeName)) {
               try {
                  elementType = ClassUtils.forName(elementTypeName, this.beanFactory.getBeanClassLoader());
                  managedArray.resolvedElementType = elementType;
               } catch (Throwable ex) {
                  throw new BeanCreationException(this.beanDefinition.getResourceDescription(), this.beanName, "Error resolving array type for " + argName, ex);
               }
            } else {
               elementType = Object.class;
            }
         }

         return this.resolveManagedArray(argName, (List<?>)value, elementType);
      } else if (value instanceof ManagedList<?> managedList) {
         return this.resolveManagedList(argName, managedList);
      } else if (value instanceof ManagedSet<?> managedSet) {
         return this.resolveManagedSet(argName, managedSet);
      } else if (value instanceof ManagedMap<?, ?> managedMap) {
         return this.resolveManagedMap(argName, managedMap);
      } else if (value instanceof ManagedProperties original) {
         Properties copy = new Properties();
         original.forEach(
            (propKey, propValue) -> {
               if (propKey instanceof TypedStringValue typedStringValuexx) {
                  propKey = this.evaluate(typedStringValuexx);
               }

               if (propValue instanceof TypedStringValue typedStringValuex) {
                  propValue = this.evaluate(typedStringValuex);
               }

               if (propKey != null && propValue != null) {
                  copy.put(propKey, propValue);
               } else {
                  throw new BeanCreationException(
                     this.beanDefinition.getResourceDescription(),
                     this.beanName,
                     "Error converting Properties key/value pair for " + argName + ": resolved to null"
                  );
               }
            }
         );
         return copy;
      } else if (value instanceof TypedStringValue typedStringValue) {
         Object valueObject = this.evaluate(typedStringValue);

         try {
            Class<?> resolvedTargetType = this.resolveTargetType(typedStringValue);
            return resolvedTargetType != null ? this.typeConverter.convertIfNecessary(valueObject, resolvedTargetType) : valueObject;
         } catch (Throwable ex) {
            throw new BeanCreationException(
               this.beanDefinition.getResourceDescription(), this.beanName, "Error converting typed String value for " + argName, ex
            );
         }
      } else {
         return value instanceof NullBean ? null : this.evaluate(value);
      }
   }

   public <T> T resolveInnerBean(@Nullable String innerBeanName, BeanDefinition innerBd, BiFunction<String, RootBeanDefinition, T> resolver) {
      String nameToUse = innerBeanName != null ? innerBeanName : "(inner bean)#" + ObjectUtils.getIdentityHexString(innerBd);
      return resolver.apply(nameToUse, this.beanFactory.getMergedBeanDefinition(nameToUse, innerBd, this.beanDefinition));
   }

   protected @Nullable Object evaluate(TypedStringValue value) {
      Object result = this.doEvaluate(value.getValue());
      if (!ObjectUtils.nullSafeEquals(result, value.getValue())) {
         value.setDynamic();
      }

      return result;
   }

   protected Object evaluate(Object value) {
      if (value instanceof String str) {
         return this.doEvaluate(str);
      } else if (value instanceof String[] values) {
         boolean actuallyResolved = false;
         Object[] resolvedValues = new Object[values.length];

         for (int i = 0; i < values.length; i++) {
            String originalValue = values[i];
            Object resolvedValue = this.doEvaluate(originalValue);
            if (resolvedValue != originalValue) {
               actuallyResolved = true;
            }

            resolvedValues[i] = resolvedValue;
         }

         return actuallyResolved ? resolvedValues : values;
      } else {
         return value;
      }
   }

   private @Nullable Object doEvaluate(@Nullable String value) {
      return this.beanFactory.evaluateBeanDefinitionString(value, this.beanDefinition);
   }

   protected @Nullable Class<?> resolveTargetType(TypedStringValue value) throws ClassNotFoundException {
      return value.hasTargetType() ? value.getTargetType() : value.resolveTargetType(this.beanFactory.getBeanClassLoader());
   }

   private @Nullable Object resolveReference(Object argName, RuntimeBeanReference ref) {
      try {
         Class<?> beanType = ref.getBeanType();
         String resolvedName = String.valueOf(this.doEvaluate(ref.getBeanName()));
         Object bean;
         if (ref.isToParent()) {
            BeanFactory parent = this.beanFactory.getParentBeanFactory();
            if (parent == null) {
               throw new BeanCreationException(
                  this.beanDefinition.getResourceDescription(),
                  this.beanName,
                  "Cannot resolve reference to bean " + ref + " in parent factory: no parent factory available"
               );
            }

            if (beanType != null) {
               bean = parent.containsBean(resolvedName) ? parent.getBean(resolvedName, beanType) : parent.getBean(beanType);
            } else {
               bean = parent.getBean(resolvedName);
            }
         } else {
            if (beanType != null) {
               if (this.beanFactory.containsBean(resolvedName)) {
                  bean = this.beanFactory.getBean(resolvedName, beanType);
               } else {
                  NamedBeanHolder<?> namedBean = this.beanFactory.resolveNamedBean(beanType);
                  bean = namedBean.getBeanInstance();
                  resolvedName = namedBean.getBeanName();
               }
            } else {
               bean = this.beanFactory.getBean(resolvedName);
            }

            this.beanFactory.registerDependentBean(resolvedName, this.beanName);
         }

         if (bean instanceof NullBean) {
            bean = null;
         }

         return bean;
      } catch (BeansException ex) {
         throw new BeanCreationException(
            this.beanDefinition.getResourceDescription(),
            this.beanName,
            "Cannot resolve reference to bean '" + ref.getBeanName() + "' while setting " + argName,
            ex
         );
      }
   }

   private @Nullable Object resolveInnerBeanValue(Object argName, String innerBeanName, RootBeanDefinition mbd) {
      try {
         String actualInnerBeanName = innerBeanName;
         if (mbd.isSingleton()) {
            actualInnerBeanName = this.adaptInnerBeanName(innerBeanName);
         }

         this.beanFactory.registerContainedBean(actualInnerBeanName, this.beanName);
         String[] dependsOn = mbd.getDependsOn();
         if (dependsOn != null) {
            for (String dependsOnBean : dependsOn) {
               this.beanFactory.registerDependentBean(dependsOnBean, actualInnerBeanName);
               this.beanFactory.getBean(dependsOnBean);
            }
         }

         Object innerBean = this.beanFactory.createBean(actualInnerBeanName, mbd, null);
         if (innerBean instanceof FactoryBean<?> factoryBean) {
            boolean synthetic = mbd.isSynthetic();
            innerBean = this.beanFactory.getObjectFromFactoryBean(factoryBean, null, actualInnerBeanName, !synthetic);
         }

         if (innerBean instanceof NullBean) {
            innerBean = null;
         }

         return innerBean;
      } catch (BeansException ex) {
         throw new BeanCreationException(
            this.beanDefinition.getResourceDescription(),
            this.beanName,
            "Cannot create inner bean '"
               + innerBeanName
               + "' "
               + (mbd.getBeanClassName() != null ? "of type [" + mbd.getBeanClassName() + "] " : "")
               + "while setting "
               + argName,
            ex
         );
      }
   }

   private String adaptInnerBeanName(String innerBeanName) {
      String actualInnerBeanName = innerBeanName;
      int counter = 0;
      String prefix = innerBeanName + "#";

      while (this.beanFactory.isBeanNameInUse(actualInnerBeanName)) {
         actualInnerBeanName = prefix + ++counter;
      }

      return actualInnerBeanName;
   }

   private Object resolveManagedArray(Object argName, List<?> ml, Class<?> elementType) {
      Object resolved = Array.newInstance(elementType, ml.size());

      for (int i = 0; i < ml.size(); i++) {
         Array.set(resolved, i, this.resolveValueIfNecessary(new BeanDefinitionValueResolver.KeyedArgName(argName, i), ml.get(i)));
      }

      return resolved;
   }

   private List<?> resolveManagedList(Object argName, List<?> ml) {
      List<Object> resolved = new ArrayList<>(ml.size());

      for (int i = 0; i < ml.size(); i++) {
         resolved.add(this.resolveValueIfNecessary(new BeanDefinitionValueResolver.KeyedArgName(argName, i), ml.get(i)));
      }

      return resolved;
   }

   private Set<?> resolveManagedSet(Object argName, Set<?> ms) {
      Set<Object> resolved = CollectionUtils.newLinkedHashSet(ms.size());
      int i = 0;

      for (Object m : ms) {
         resolved.add(this.resolveValueIfNecessary(new BeanDefinitionValueResolver.KeyedArgName(argName, i), m));
         i++;
      }

      return resolved;
   }

   private Map<?, ?> resolveManagedMap(Object argName, Map<?, ?> mm) {
      Map<Object, Object> resolved = CollectionUtils.newLinkedHashMap(mm.size());
      mm.forEach((key, value) -> {
         Object resolvedKey = this.resolveValueIfNecessary(argName, key);
         Object resolvedValue = this.resolveValueIfNecessary(new BeanDefinitionValueResolver.KeyedArgName(argName, key), value);
         resolved.put(resolvedKey, resolvedValue);
      });
      return resolved;
   }

   private static class KeyedArgName {
      private final Object argName;
      private final Object key;

      public KeyedArgName(Object argName, Object key) {
         this.argName = argName;
         this.key = key;
      }

      @Override
      public String toString() {
         return this.argName + " with key [" + this.key + "]";
      }
   }
}
