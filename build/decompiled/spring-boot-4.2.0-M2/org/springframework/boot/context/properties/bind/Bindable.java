package org.springframework.boot.context.properties.bind;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

public final class Bindable<T> {
   private static final Annotation[] NO_ANNOTATIONS = new Annotation[0];
   private static final EnumSet<Bindable.BindRestriction> NO_BIND_RESTRICTIONS = EnumSet.noneOf(Bindable.BindRestriction.class);
   private final ResolvableType type;
   private final ResolvableType boxedType;
   private final @Nullable Supplier<T> value;
   private final Annotation[] annotations;
   private final EnumSet<Bindable.BindRestriction> bindRestrictions;
   private final @Nullable BindMethod bindMethod;

   private Bindable(
      ResolvableType type,
      ResolvableType boxedType,
      @Nullable Supplier<T> value,
      Annotation[] annotations,
      EnumSet<Bindable.BindRestriction> bindRestrictions,
      @Nullable BindMethod bindMethod
   ) {
      this.type = type;
      this.boxedType = boxedType;
      this.value = value;
      this.annotations = annotations;
      this.bindRestrictions = bindRestrictions;
      this.bindMethod = bindMethod;
   }

   public ResolvableType getType() {
      return this.type;
   }

   public ResolvableType getBoxedType() {
      return this.boxedType;
   }

   public @Nullable Supplier<T> getValue() {
      return this.value;
   }

   public Annotation[] getAnnotations() {
      return this.annotations;
   }

   public <A extends Annotation> @Nullable A getAnnotation(Class<A> type) {
      for (Annotation annotation : this.annotations) {
         if (type.isInstance(annotation)) {
            return (A)annotation;
         }
      }

      return null;
   }

   public boolean hasBindRestriction(Bindable.BindRestriction bindRestriction) {
      return this.bindRestrictions.contains(bindRestriction);
   }

   public @Nullable BindMethod getBindMethod() {
      return this.bindMethod;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (obj != null && this.getClass() == obj.getClass()) {
         Bindable<?> other = (Bindable<?>)obj;
         boolean result = true;
         result = result && this.nullSafeEquals(this.type.resolve(), other.type.resolve());
         result = result && this.nullSafeEquals(this.annotations, other.annotations);
         result = result && this.nullSafeEquals(this.bindRestrictions, other.bindRestrictions);
         return result && this.nullSafeEquals(this.bindMethod, other.bindMethod);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return ObjectUtils.nullSafeHash(new Object[]{this.type, this.annotations, this.bindRestrictions, this.bindMethod});
   }

   @Override
   public String toString() {
      ToStringCreator creator = new ToStringCreator(this);
      creator.append("type", this.type);
      creator.append("value", this.value != null ? "provided" : "none");
      creator.append("annotations", this.annotations);
      creator.append("bindMethod", this.bindMethod);
      return creator.toString();
   }

   private boolean nullSafeEquals(@Nullable Object o1, @Nullable Object o2) {
      return ObjectUtils.nullSafeEquals(o1, o2);
   }

   public Bindable<T> withAnnotations(Annotation @Nullable ... annotations) {
      return new Bindable<>(this.type, this.boxedType, this.value, annotations != null ? annotations : NO_ANNOTATIONS, NO_BIND_RESTRICTIONS, this.bindMethod);
   }

   public Bindable<T> withExistingValue(@Nullable T existingValue) {
      Assert.isTrue(
         existingValue == null || this.type.isArray() || this.boxedTypeIsInstanceOf(existingValue), () -> "'existingValue' must be an instance of " + this.type
      );
      Assert.state(this.bindMethod != BindMethod.VALUE_OBJECT, () -> "An existing value cannot be provided when binding as a value object");
      Supplier<T> value = existingValue != null ? () -> existingValue : null;
      return new Bindable<>(this.type, this.boxedType, value, this.annotations, this.bindRestrictions, BindMethod.JAVA_BEAN);
   }

   private boolean boxedTypeIsInstanceOf(T existingValue) {
      Class<?> resolved = this.boxedType.resolve();
      return resolved != null && resolved.isInstance(existingValue);
   }

   public Bindable<T> withSuppliedValue(@Nullable Supplier<T> suppliedValue) {
      return new Bindable<>(this.type, this.boxedType, suppliedValue, this.annotations, this.bindRestrictions, this.bindMethod);
   }

   public Bindable<T> withBindRestrictions(Bindable.BindRestriction... additionalRestrictions) {
      EnumSet<Bindable.BindRestriction> bindRestrictions = EnumSet.copyOf(this.bindRestrictions);
      bindRestrictions.addAll(Arrays.asList(additionalRestrictions));
      return new Bindable<>(this.type, this.boxedType, this.value, this.annotations, bindRestrictions, this.bindMethod);
   }

   public Bindable<T> withBindMethod(@Nullable BindMethod bindMethod) {
      Assert.state(bindMethod != BindMethod.VALUE_OBJECT || this.value == null, () -> "Value object binding cannot be used with an existing or supplied value");
      return new Bindable<>(this.type, this.boxedType, this.value, this.annotations, this.bindRestrictions, bindMethod);
   }

   public static <T> Bindable<T> ofInstance(T instance) {
      Assert.notNull(instance, "'instance' must not be null");
      Class<T> type = (Class<T>)instance.getClass();
      return of(type).withExistingValue(instance);
   }

   public static <T> Bindable<T> of(Class<T> type) {
      Assert.notNull(type, "'type' must not be null");
      return of(ResolvableType.forClass(type));
   }

   public static <E> Bindable<List<E>> listOf(Class<E> elementType) {
      return of(ResolvableType.forClassWithGenerics(List.class, new Class[]{elementType}));
   }

   public static <E> Bindable<Set<E>> setOf(Class<E> elementType) {
      return of(ResolvableType.forClassWithGenerics(Set.class, new Class[]{elementType}));
   }

   public static <K, V> Bindable<Map<K, V>> mapOf(Class<K> keyType, Class<V> valueType) {
      return of(ResolvableType.forClassWithGenerics(Map.class, new Class[]{keyType, valueType}));
   }

   public static <T> Bindable<T> of(ResolvableType type) {
      Assert.notNull(type, "'type' must not be null");
      ResolvableType boxedType = box(type);
      return new Bindable<>(type, boxedType, null, NO_ANNOTATIONS, NO_BIND_RESTRICTIONS, null);
   }

   private static ResolvableType box(ResolvableType type) {
      Class<?> resolved = type.resolve();
      if (resolved != null && resolved.isPrimitive()) {
         Object array = Array.newInstance(resolved, 1);
         Class<?> wrapperType = Array.get(array, 0).getClass();
         return ResolvableType.forClass(wrapperType);
      } else {
         return resolved != null && resolved.isArray() ? ResolvableType.forArrayComponent(box(type.getComponentType())) : type;
      }
   }

   public enum BindRestriction {
      NO_DIRECT_PROPERTY;
   }
}
