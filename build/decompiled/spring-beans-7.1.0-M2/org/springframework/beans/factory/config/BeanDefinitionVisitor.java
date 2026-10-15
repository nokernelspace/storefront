package org.springframework.beans.factory.config;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringValueResolver;

public class BeanDefinitionVisitor {
   private @Nullable StringValueResolver valueResolver;

   public BeanDefinitionVisitor(StringValueResolver valueResolver) {
      Assert.notNull(valueResolver, "StringValueResolver must not be null");
      this.valueResolver = valueResolver;
   }

   protected BeanDefinitionVisitor() {
   }

   public void visitBeanDefinition(BeanDefinition beanDefinition) {
      this.visitParentName(beanDefinition);
      this.visitBeanClassName(beanDefinition);
      this.visitFactoryBeanName(beanDefinition);
      this.visitFactoryMethodName(beanDefinition);
      this.visitScope(beanDefinition);
      if (beanDefinition.hasPropertyValues()) {
         this.visitPropertyValues(beanDefinition.getPropertyValues());
      }

      if (beanDefinition.hasConstructorArgumentValues()) {
         ConstructorArgumentValues cas = beanDefinition.getConstructorArgumentValues();
         this.visitIndexedArgumentValues(cas.getIndexedArgumentValues());
         this.visitGenericArgumentValues(cas.getGenericArgumentValues());
      }
   }

   protected void visitParentName(BeanDefinition beanDefinition) {
      String parentName = beanDefinition.getParentName();
      if (parentName != null) {
         String resolvedName = this.resolveStringValue(parentName);
         if (!parentName.equals(resolvedName)) {
            beanDefinition.setParentName(resolvedName);
         }
      }
   }

   protected void visitBeanClassName(BeanDefinition beanDefinition) {
      String beanClassName = beanDefinition.getBeanClassName();
      if (beanClassName != null) {
         String resolvedName = this.resolveStringValue(beanClassName);
         if (!beanClassName.equals(resolvedName)) {
            beanDefinition.setBeanClassName(resolvedName);
         }
      }
   }

   protected void visitFactoryBeanName(BeanDefinition beanDefinition) {
      String factoryBeanName = beanDefinition.getFactoryBeanName();
      if (factoryBeanName != null) {
         String resolvedName = this.resolveStringValue(factoryBeanName);
         if (!factoryBeanName.equals(resolvedName)) {
            beanDefinition.setFactoryBeanName(resolvedName);
         }
      }
   }

   protected void visitFactoryMethodName(BeanDefinition beanDefinition) {
      String factoryMethodName = beanDefinition.getFactoryMethodName();
      if (factoryMethodName != null) {
         String resolvedName = this.resolveStringValue(factoryMethodName);
         if (!factoryMethodName.equals(resolvedName)) {
            beanDefinition.setFactoryMethodName(resolvedName);
         }
      }
   }

   protected void visitScope(BeanDefinition beanDefinition) {
      String scope = beanDefinition.getScope();
      if (scope != null) {
         String resolvedScope = this.resolveStringValue(scope);
         if (!scope.equals(resolvedScope)) {
            beanDefinition.setScope(resolvedScope);
         }
      }
   }

   protected void visitPropertyValues(MutablePropertyValues pvs) {
      PropertyValue[] pvArray = pvs.getPropertyValues();

      for (PropertyValue pv : pvArray) {
         Object newVal = this.resolveValue(pv.getValue());
         if (!ObjectUtils.nullSafeEquals(newVal, pv.getValue())) {
            pvs.add(pv.getName(), newVal);
         }
      }
   }

   protected void visitIndexedArgumentValues(Map<Integer, ConstructorArgumentValues.ValueHolder> ias) {
      for (ConstructorArgumentValues.ValueHolder valueHolder : ias.values()) {
         Object newVal = this.resolveValue(valueHolder.getValue());
         if (!ObjectUtils.nullSafeEquals(newVal, valueHolder.getValue())) {
            valueHolder.setValue(newVal);
         }
      }
   }

   protected void visitGenericArgumentValues(List<ConstructorArgumentValues.ValueHolder> gas) {
      for (ConstructorArgumentValues.ValueHolder valueHolder : gas) {
         Object newVal = this.resolveValue(valueHolder.getValue());
         if (!ObjectUtils.nullSafeEquals(newVal, valueHolder.getValue())) {
            valueHolder.setValue(newVal);
         }
      }
   }

   protected @Nullable Object resolveValue(@Nullable Object value) {
      if (value instanceof BeanDefinition beanDef) {
         this.visitBeanDefinition(beanDef);
      } else if (value instanceof BeanDefinitionHolder beanDefHolder) {
         this.visitBeanDefinition(beanDefHolder.getBeanDefinition());
      } else if (value instanceof RuntimeBeanReference ref) {
         String newBeanName = this.resolveStringValue(ref.getBeanName());
         if (newBeanName == null) {
            return null;
         }

         if (!newBeanName.equals(ref.getBeanName())) {
            return new RuntimeBeanReference(newBeanName);
         }
      } else if (value instanceof RuntimeBeanNameReference ref) {
         String newBeanName = this.resolveStringValue(ref.getBeanName());
         if (newBeanName == null) {
            return null;
         }

         if (!newBeanName.equals(ref.getBeanName())) {
            return new RuntimeBeanNameReference(newBeanName);
         }
      } else if (value instanceof Object[] array) {
         this.visitArray(array);
      } else if (value instanceof List list) {
         this.visitList(list);
      } else if (value instanceof Set set) {
         this.visitSet(set);
      } else if (value instanceof Map map) {
         this.visitMap(map);
      } else if (value instanceof TypedStringValue typedStringValue) {
         String stringValue = typedStringValue.getValue();
         if (stringValue != null) {
            String visitedString = this.resolveStringValue(stringValue);
            typedStringValue.setValue(visitedString);
         }
      } else if (value instanceof String strValue) {
         return this.resolveStringValue(strValue);
      }

      return value;
   }

   protected void visitArray(@Nullable Object[] arrayVal) {
      for (int i = 0; i < arrayVal.length; i++) {
         Object elem = arrayVal[i];
         Object newVal = this.resolveValue(elem);
         if (!ObjectUtils.nullSafeEquals(newVal, elem)) {
            arrayVal[i] = newVal;
         }
      }
   }

   protected void visitList(List listVal) {
      for (int i = 0; i < listVal.size(); i++) {
         Object elem = listVal.get(i);
         Object newVal = this.resolveValue(elem);
         if (!ObjectUtils.nullSafeEquals(newVal, elem)) {
            listVal.set(i, newVal);
         }
      }
   }

   protected void visitSet(Set setVal) {
      Set newContent = new LinkedHashSet();
      boolean entriesModified = false;

      for (Object elem : setVal) {
         int elemHash = elem != null ? elem.hashCode() : 0;
         Object newVal = this.resolveValue(elem);
         int newValHash = newVal != null ? newVal.hashCode() : 0;
         newContent.add(newVal);
         entriesModified = entriesModified || newVal != elem || newValHash != elemHash;
      }

      if (entriesModified) {
         setVal.clear();
         setVal.addAll(newContent);
      }
   }

   protected void visitMap(Map<?, ?> mapVal) {
      Map newContent = new LinkedHashMap();
      boolean entriesModified = false;

      for (Entry entry : mapVal.entrySet()) {
         Object key = entry.getKey();
         int keyHash = key != null ? key.hashCode() : 0;
         Object newKey = this.resolveValue(key);
         int newKeyHash = newKey != null ? newKey.hashCode() : 0;
         Object val = entry.getValue();
         Object newVal = this.resolveValue(val);
         newContent.put(newKey, newVal);
         entriesModified = entriesModified || newVal != val || newKey != key || newKeyHash != keyHash;
      }

      if (entriesModified) {
         mapVal.clear();
         mapVal.putAll(newContent);
      }
   }

   protected @Nullable String resolveStringValue(String strVal) {
      if (this.valueResolver == null) {
         throw new IllegalStateException(
            "No StringValueResolver specified - pass a resolver object into the constructor or override the 'resolveStringValue' method"
         );
      }

      String resolvedValue = this.valueResolver.resolveStringValue(strVal);
      return strVal.equals(resolvedValue) ? strVal : resolvedValue;
   }
}
