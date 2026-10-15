package org.springframework.core.convert;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementAdapter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.lang.Contract;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

public class TypeDescriptor implements Serializable {
   private static final Map<Class<?>, TypeDescriptor> commonTypesCache;
   private static final Class<?>[] CACHED_COMMON_TYPES = new Class[]{
      boolean.class,
      Boolean.class,
      byte.class,
      Byte.class,
      char.class,
      Character.class,
      double.class,
      Double.class,
      float.class,
      Float.class,
      int.class,
      Integer.class,
      long.class,
      Long.class,
      short.class,
      Short.class,
      String.class,
      Object.class
   };
   private final Class<?> type;
   private final ResolvableType resolvableType;
   private final TypeDescriptor.AnnotatedElementSupplier annotatedElementSupplier;
   private volatile @Nullable AnnotatedElementAdapter annotatedElement;

   public TypeDescriptor(MethodParameter methodParameter) {
      this.resolvableType = ResolvableType.forMethodParameter(methodParameter);
      this.type = this.resolvableType.resolve(methodParameter.getNestedParameterType());
      this.annotatedElementSupplier = () -> AnnotatedElementAdapter.from(
         methodParameter.getParameterIndex() == -1 ? methodParameter.getMethodAnnotations() : methodParameter.getParameterAnnotations()
      );
   }

   public TypeDescriptor(Field field) {
      this.resolvableType = ResolvableType.forField(field);
      this.type = this.resolvableType.resolve(field.getType());
      this.annotatedElementSupplier = () -> AnnotatedElementAdapter.from(field.getAnnotations());
   }

   public TypeDescriptor(Property property) {
      Assert.notNull(property, "Property must not be null");
      this.resolvableType = ResolvableType.forMethodParameter(property.getMethodParameter());
      this.type = this.resolvableType.resolve(property.getType());
      this.annotatedElementSupplier = () -> AnnotatedElementAdapter.from(property.getAnnotations());
   }

   public TypeDescriptor(ResolvableType resolvableType, @Nullable Class<?> type, Annotation @Nullable [] annotations) {
      this.resolvableType = resolvableType;
      this.type = type != null ? type : resolvableType.toClass();
      this.annotatedElementSupplier = () -> AnnotatedElementAdapter.from(annotations);
   }

   public Class<?> getObjectType() {
      return ClassUtils.resolvePrimitiveIfNecessary(this.getType());
   }

   public Class<?> getType() {
      return this.type;
   }

   public ResolvableType getResolvableType() {
      return this.resolvableType;
   }

   public Object getSource() {
      return this.resolvableType.getSource();
   }

   public @Nullable TypeDescriptor nested(int nestingLevel) {
      ResolvableType nested = this.resolvableType;

      for (int i = 0; i < nestingLevel; i++) {
         if (Object.class != nested.getType()) {
            nested = nested.getNested(2);
         }
      }

      return nested == ResolvableType.NONE ? null : this.getRelatedIfResolvable(nested);
   }

   public TypeDescriptor narrow(@Nullable Object value) {
      if (value == null) {
         return this;
      }

      ResolvableType narrowed = ResolvableType.forType(value.getClass(), this.getResolvableType());
      return new TypeDescriptor(narrowed, value.getClass(), this.getAnnotations());
   }

   public @Nullable TypeDescriptor upcast(@Nullable Class<?> superType) {
      if (superType == null) {
         return null;
      }

      Assert.isAssignable(superType, this.getType());
      return new TypeDescriptor(this.getResolvableType().as(superType), superType, this.getAnnotations());
   }

   public String getName() {
      return ClassUtils.getQualifiedName(this.getType());
   }

   public boolean isPrimitive() {
      return this.getType().isPrimitive();
   }

   private AnnotatedElementAdapter getAnnotatedElement() {
      AnnotatedElementAdapter annotatedElement = this.annotatedElement;
      if (annotatedElement == null) {
         annotatedElement = this.annotatedElementSupplier.get();
         this.annotatedElement = annotatedElement;
      }

      return annotatedElement;
   }

   public Annotation[] getAnnotations() {
      return this.getAnnotatedElement().getAnnotations();
   }

   public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
      AnnotatedElementAdapter annotatedElement = this.getAnnotatedElement();
      return annotatedElement.isEmpty() ? false : AnnotatedElementUtils.isAnnotated(annotatedElement, annotationType);
   }

   public <T extends Annotation> @Nullable T getAnnotation(Class<T> annotationType) {
      AnnotatedElementAdapter annotatedElement = this.getAnnotatedElement();
      return annotatedElement.isEmpty() ? null : AnnotatedElementUtils.getMergedAnnotation(annotatedElement, annotationType);
   }

   public boolean isAssignableTo(TypeDescriptor typeDescriptor) {
      boolean typesAssignable = typeDescriptor.getObjectType().isAssignableFrom(this.getObjectType());
      if (!typesAssignable) {
         return false;
      } else if (this.isArray() && typeDescriptor.isArray()) {
         return this.isNestedAssignable(this.getElementTypeDescriptor(), typeDescriptor.getElementTypeDescriptor());
      } else if (this.isCollection() && typeDescriptor.isCollection()) {
         return this.isNestedAssignable(this.getElementTypeDescriptor(), typeDescriptor.getElementTypeDescriptor());
      } else {
         return this.isMap() && typeDescriptor.isMap()
            ? this.isNestedAssignable(this.getMapKeyTypeDescriptor(), typeDescriptor.getMapKeyTypeDescriptor())
               && this.isNestedAssignable(this.getMapValueTypeDescriptor(), typeDescriptor.getMapValueTypeDescriptor())
            : true;
      }
   }

   private boolean isNestedAssignable(@Nullable TypeDescriptor nestedTypeDescriptor, @Nullable TypeDescriptor otherNestedTypeDescriptor) {
      return nestedTypeDescriptor == null || otherNestedTypeDescriptor == null || nestedTypeDescriptor.isAssignableTo(otherNestedTypeDescriptor);
   }

   public boolean isCollection() {
      return Collection.class.isAssignableFrom(this.getType());
   }

   public boolean isArray() {
      return this.getType().isArray();
   }

   public @Nullable TypeDescriptor getElementTypeDescriptor() {
      if (this.getResolvableType().isArray()) {
         return new TypeDescriptor(this.getResolvableType().getComponentType(), null, this.getAnnotations());
      } else {
         return Stream.class.isAssignableFrom(this.getType())
            ? this.getRelatedIfResolvable(this.getResolvableType().as(Stream.class).getGeneric(0))
            : this.getRelatedIfResolvable(this.getResolvableType().asCollection().getGeneric(0));
      }
   }

   public @Nullable TypeDescriptor elementTypeDescriptor(Object element) {
      return this.narrow(element, this.getElementTypeDescriptor());
   }

   public boolean isMap() {
      return Map.class.isAssignableFrom(this.getType());
   }

   public @Nullable TypeDescriptor getMapKeyTypeDescriptor() {
      Assert.state(this.isMap(), "Not a [java.util.Map]");
      return this.getRelatedIfResolvable(this.getResolvableType().asMap().getGeneric(0));
   }

   public @Nullable TypeDescriptor getMapKeyTypeDescriptor(Object mapKey) {
      return this.narrow(mapKey, this.getMapKeyTypeDescriptor());
   }

   public @Nullable TypeDescriptor getMapValueTypeDescriptor() {
      Assert.state(this.isMap(), "Not a [java.util.Map]");
      return this.getRelatedIfResolvable(this.getResolvableType().asMap().getGeneric(1));
   }

   public @Nullable TypeDescriptor getMapValueTypeDescriptor(@Nullable Object mapValue) {
      return this.narrow(mapValue, this.getMapValueTypeDescriptor());
   }

   private @Nullable TypeDescriptor getRelatedIfResolvable(ResolvableType type) {
      return type.resolve() == null ? null : new TypeDescriptor(type, null, this.getAnnotations());
   }

   private @Nullable TypeDescriptor narrow(@Nullable Object value, @Nullable TypeDescriptor typeDescriptor) {
      if (typeDescriptor != null) {
         return typeDescriptor.narrow(value);
      } else {
         return value != null ? this.narrow(value) : null;
      }
   }

   @Override
   public boolean equals(@Nullable Object other) {
      if (this == other) {
         return true;
      }

      if (other instanceof TypeDescriptor otherDesc) {
         if (this.getType() != otherDesc.getType()) {
            return false;
         } else {
            return !this.annotationsMatch(otherDesc)
               ? false
               : Arrays.equals(this.getResolvableType().getGenerics(), otherDesc.getResolvableType().getGenerics());
         }
      } else {
         return false;
      }
   }

   private boolean annotationsMatch(TypeDescriptor otherDesc) {
      Annotation[] anns = this.getAnnotations();
      Annotation[] otherAnns = otherDesc.getAnnotations();
      if (anns == otherAnns) {
         return true;
      }

      if (anns.length != otherAnns.length) {
         return false;
      }

      if (anns.length > 0) {
         for (int i = 0; i < anns.length; i++) {
            if (!this.annotationEquals(anns[i], otherAnns[i])) {
               return false;
            }
         }
      }

      return true;
   }

   private boolean annotationEquals(Annotation ann, Annotation otherAnn) {
      return ann == otherAnn || ann.getClass() == otherAnn.getClass() && ann.equals(otherAnn);
   }

   @Override
   public int hashCode() {
      return this.getType().hashCode();
   }

   @Override
   public String toString() {
      StringBuilder builder = new StringBuilder();

      for (Annotation ann : this.getAnnotations()) {
         builder.append('@').append(ClassUtils.getCanonicalName(ann.annotationType())).append(' ');
      }

      builder.append(this.getResolvableType());
      return builder.toString();
   }

   @Contract("!null -> !null; null -> null")
   public static @Nullable TypeDescriptor forObject(@Nullable Object source) {
      return source != null ? valueOf(source.getClass()) : null;
   }

   public static TypeDescriptor valueOf(@Nullable Class<?> type) {
      if (type == null) {
         type = Object.class;
      }

      TypeDescriptor desc = commonTypesCache.get(type);
      return desc != null ? desc : new TypeDescriptor(ResolvableType.forClass(type), null, null);
   }

   public static TypeDescriptor collection(Class<?> collectionType, @Nullable TypeDescriptor elementTypeDescriptor) {
      Assert.notNull(collectionType, "Collection type must not be null");
      if (!Collection.class.isAssignableFrom(collectionType)) {
         throw new IllegalArgumentException("Collection type must be a [java.util.Collection]");
      }

      ResolvableType element = elementTypeDescriptor != null ? elementTypeDescriptor.resolvableType : null;
      return new TypeDescriptor(ResolvableType.forClassWithGenerics(collectionType, element), null, null);
   }

   public static TypeDescriptor map(Class<?> mapType, @Nullable TypeDescriptor keyTypeDescriptor, @Nullable TypeDescriptor valueTypeDescriptor) {
      Assert.notNull(mapType, "Map type must not be null");
      if (!Map.class.isAssignableFrom(mapType)) {
         throw new IllegalArgumentException("Map type must be a [java.util.Map]");
      }

      ResolvableType key = keyTypeDescriptor != null ? keyTypeDescriptor.resolvableType : null;
      ResolvableType value = valueTypeDescriptor != null ? valueTypeDescriptor.resolvableType : null;
      return new TypeDescriptor(ResolvableType.forClassWithGenerics(mapType, key, value), null, null);
   }

   @Contract("!null -> !null; null -> null")
   public static @Nullable TypeDescriptor array(@Nullable TypeDescriptor elementTypeDescriptor) {
      return elementTypeDescriptor == null
         ? null
         : new TypeDescriptor(ResolvableType.forArrayComponent(elementTypeDescriptor.resolvableType), null, elementTypeDescriptor.getAnnotations());
   }

   public static @Nullable TypeDescriptor nested(MethodParameter methodParameter, int nestingLevel) {
      if (methodParameter.getNestingLevel() != 1) {
         throw new IllegalArgumentException(
            "MethodParameter nesting level must be 1: use the nestingLevel parameter to specify the desired nestingLevel for nested type traversal"
         );
      } else {
         return new TypeDescriptor(methodParameter).nested(nestingLevel);
      }
   }

   public static @Nullable TypeDescriptor nested(Field field, int nestingLevel) {
      return new TypeDescriptor(field).nested(nestingLevel);
   }

   public static @Nullable TypeDescriptor nested(Property property, int nestingLevel) {
      return new TypeDescriptor(property).nested(nestingLevel);
   }

   static {
      Map<Class<?>, TypeDescriptor> commonTypes = CollectionUtils.newHashMap(CACHED_COMMON_TYPES.length);

      for (Class<?> preCachedClass : CACHED_COMMON_TYPES) {
         commonTypes.put(preCachedClass, new TypeDescriptor(ResolvableType.forClass(preCachedClass), null, null));
      }

      commonTypesCache = Map.copyOf(commonTypes);
   }

   private interface AnnotatedElementSupplier extends Supplier<AnnotatedElementAdapter>, Serializable {
   }
}
