package org.springframework.beans.factory.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.TypeConverter;
import org.springframework.core.ResolvableType;

public class ListFactoryBean extends AbstractFactoryBean<List<Object>> {
   private @Nullable List<?> sourceList;
   private @Nullable Class<? extends List> targetListClass;

   public void setSourceList(List<?> sourceList) {
      this.sourceList = sourceList;
   }

   public void setTargetListClass(@Nullable Class<? extends List> targetListClass) {
      if (targetListClass == null) {
         throw new IllegalArgumentException("'targetListClass' must not be null");
      }

      if (!List.class.isAssignableFrom(targetListClass)) {
         throw new IllegalArgumentException("'targetListClass' must implement [java.util.List]");
      }

      this.targetListClass = targetListClass;
   }

   @Override
   public Class<List> getObjectType() {
      return List.class;
   }

   protected List<Object> createInstance() {
      if (this.sourceList == null) {
         throw new IllegalArgumentException("'sourceList' is required");
      }

      List<Object> result = null;
      if (this.targetListClass != null) {
         result = BeanUtils.instantiateClass((Class<List<Object>>)this.targetListClass);
      } else {
         result = new ArrayList<>(this.sourceList.size());
      }

      Class<?> valueType = null;
      if (this.targetListClass != null) {
         valueType = ResolvableType.forClass(this.targetListClass).asCollection().resolveGeneric(new int[0]);
      }

      if (valueType != null) {
         TypeConverter converter = this.getBeanTypeConverter();

         for (Object elem : this.sourceList) {
            result.add(converter.convertIfNecessary(elem, valueType));
         }
      } else {
         result.addAll((Collection<? extends Object>)this.sourceList);
      }

      return result;
   }
}
