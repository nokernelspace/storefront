package org.springframework.beans.factory.config;

import java.util.Comparator;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanMetadataElement;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;

public class TypedStringValue implements BeanMetadataElement, Comparable<TypedStringValue> {
   private @Nullable String value;
   private volatile @Nullable Object targetType;
   private @Nullable Object source;
   private @Nullable String specifiedTypeName;
   private volatile boolean dynamic;

   public TypedStringValue(@Nullable String value) {
      this.setValue(value);
   }

   public TypedStringValue(@Nullable String value, Class<?> targetType) {
      this.setValue(value);
      this.setTargetType(targetType);
   }

   public TypedStringValue(@Nullable String value, String targetTypeName) {
      this.setValue(value);
      this.setTargetTypeName(targetTypeName);
   }

   public void setValue(@Nullable String value) {
      this.value = value;
   }

   public @Nullable String getValue() {
      return this.value;
   }

   public void setTargetType(Class<?> targetType) {
      Assert.notNull(targetType, "'targetType' must not be null");
      this.targetType = targetType;
   }

   public Class<?> getTargetType() {
      if (this.targetType instanceof Class<?> clazz) {
         return clazz;
      } else {
         throw new IllegalStateException("Typed String value does not carry a resolved target type");
      }
   }

   public void setTargetTypeName(@Nullable String targetTypeName) {
      this.targetType = targetTypeName;
   }

   public @Nullable String getTargetTypeName() {
      Object targetTypeValue = this.targetType;
      return targetTypeValue instanceof Class<?> clazz ? clazz.getName() : (String)targetTypeValue;
   }

   public boolean hasTargetType() {
      return this.targetType instanceof Class;
   }

   public @Nullable Class<?> resolveTargetType(@Nullable ClassLoader classLoader) throws ClassNotFoundException {
      String typeName = this.getTargetTypeName();
      if (typeName == null) {
         return null;
      }

      Class<?> resolvedClass = ClassUtils.forName(typeName, classLoader);
      this.targetType = resolvedClass;
      return resolvedClass;
   }

   public void setSource(@Nullable Object source) {
      this.source = source;
   }

   @Override
   public @Nullable Object getSource() {
      return this.source;
   }

   public void setSpecifiedTypeName(@Nullable String specifiedTypeName) {
      this.specifiedTypeName = specifiedTypeName;
   }

   public @Nullable String getSpecifiedTypeName() {
      return this.specifiedTypeName;
   }

   public void setDynamic() {
      this.dynamic = true;
   }

   public boolean isDynamic() {
      return this.dynamic;
   }

   public int compareTo(@Nullable TypedStringValue o) {
      return Comparator.comparing(TypedStringValue::getValue).compare(this, o);
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof TypedStringValue that
            && ObjectUtils.nullSafeEquals(this.value, that.value)
            && ObjectUtils.nullSafeEquals(this.targetType, that.targetType);
   }

   @Override
   public int hashCode() {
      return ObjectUtils.nullSafeHash(new Object[]{this.value, this.targetType});
   }

   @Override
   public String toString() {
      return "TypedStringValue: value [" + this.value + "], target type [" + this.targetType + "]";
   }
}
