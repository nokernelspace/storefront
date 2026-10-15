package org.springframework.beans.factory.config;

import java.util.Collection;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.TypeConverter;
import org.springframework.core.ResolvableType;
import org.springframework.util.CollectionUtils;

public class SetFactoryBean extends AbstractFactoryBean<Set<Object>> {
   private @Nullable Set<?> sourceSet;
   private @Nullable Class<? extends Set> targetSetClass;

   public void setSourceSet(Set<?> sourceSet) {
      this.sourceSet = sourceSet;
   }

   public void setTargetSetClass(@Nullable Class<? extends Set> targetSetClass) {
      if (targetSetClass == null) {
         throw new IllegalArgumentException("'targetSetClass' must not be null");
      }

      if (!Set.class.isAssignableFrom(targetSetClass)) {
         throw new IllegalArgumentException("'targetSetClass' must implement [java.util.Set]");
      }

      this.targetSetClass = targetSetClass;
   }

   @Override
   public Class<Set> getObjectType() {
      return Set.class;
   }

   protected Set<Object> createInstance() {
      if (this.sourceSet == null) {
         throw new IllegalArgumentException("'sourceSet' is required");
      }

      Set<Object> result = null;
      if (this.targetSetClass != null) {
         result = BeanUtils.instantiateClass((Class<Set<Object>>)this.targetSetClass);
      } else {
         result = CollectionUtils.newLinkedHashSet(this.sourceSet.size());
      }

      Class<?> valueType = null;
      if (this.targetSetClass != null) {
         valueType = ResolvableType.forClass(this.targetSetClass).asCollection().resolveGeneric(new int[0]);
      }

      if (valueType != null) {
         TypeConverter converter = this.getBeanTypeConverter();

         for (Object elem : this.sourceSet) {
            result.add(converter.convertIfNecessary(elem, valueType));
         }
      } else {
         result.addAll((Collection<? extends Object>)this.sourceSet);
      }

      return result;
   }
}
