package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

final class TypeMappedAnnotation<A extends Annotation> extends AbstractMergedAnnotation<A> {
   private static final Map<Class<?>, Object> EMPTY_ARRAYS = Map.of(
      boolean.class,
      new boolean[0],
      byte.class,
      new byte[0],
      char.class,
      new char[0],
      double.class,
      new double[0],
      float.class,
      new float[0],
      int.class,
      new int[0],
      long.class,
      new long[0],
      short.class,
      new short[0],
      String.class,
      new String[0]
   );
   private final AnnotationTypeMapping mapping;
   private final @Nullable ClassLoader classLoader;
   private final @Nullable Object source;
   private final @Nullable Object rootAttributes;
   private final ValueExtractor valueExtractor;
   private final int aggregateIndex;
   private final boolean useMergedValues;
   private final @Nullable Predicate<String> attributeFilter;
   private final int[] resolvedRootMirrors;
   private final int[] resolvedMirrors;

   private TypeMappedAnnotation(
      AnnotationTypeMapping mapping,
      @Nullable ClassLoader classLoader,
      @Nullable Object source,
      @Nullable Object rootAttributes,
      ValueExtractor valueExtractor,
      int aggregateIndex
   ) {
      this(mapping, classLoader, source, rootAttributes, valueExtractor, aggregateIndex, null);
   }

   private TypeMappedAnnotation(
      AnnotationTypeMapping mapping,
      @Nullable ClassLoader classLoader,
      @Nullable Object source,
      @Nullable Object rootAttributes,
      ValueExtractor valueExtractor,
      int aggregateIndex,
      int @Nullable [] resolvedRootMirrors
   ) {
      this.mapping = mapping;
      this.classLoader = classLoader;
      this.source = source;
      this.rootAttributes = rootAttributes;
      this.valueExtractor = valueExtractor;
      this.aggregateIndex = aggregateIndex;
      this.useMergedValues = true;
      this.attributeFilter = null;
      this.resolvedRootMirrors = resolvedRootMirrors != null
         ? resolvedRootMirrors
         : mapping.getRoot().getMirrorSets().resolve(source, rootAttributes, this.valueExtractor);
      this.resolvedMirrors = this.getDistance() == 0
         ? this.resolvedRootMirrors
         : mapping.getMirrorSets().resolve(source, this, this::getValueForMirrorResolution);
   }

   private TypeMappedAnnotation(
      AnnotationTypeMapping mapping,
      @Nullable ClassLoader classLoader,
      @Nullable Object source,
      @Nullable Object rootAnnotation,
      ValueExtractor valueExtractor,
      int aggregateIndex,
      boolean useMergedValues,
      @Nullable Predicate<String> attributeFilter,
      int[] resolvedRootMirrors,
      int[] resolvedMirrors
   ) {
      this.classLoader = classLoader;
      this.source = source;
      this.rootAttributes = rootAnnotation;
      this.valueExtractor = valueExtractor;
      this.mapping = mapping;
      this.aggregateIndex = aggregateIndex;
      this.useMergedValues = useMergedValues;
      this.attributeFilter = attributeFilter;
      this.resolvedRootMirrors = resolvedRootMirrors;
      this.resolvedMirrors = resolvedMirrors;
   }

   @Override
   public Class<A> getType() {
      return (Class<A>)this.mapping.getAnnotationType();
   }

   @Override
   public List<Class<? extends Annotation>> getMetaTypes() {
      return this.mapping.getMetaTypes();
   }

   @Override
   public boolean isPresent() {
      return true;
   }

   @Override
   public int getDistance() {
      return this.mapping.getDistance();
   }

   @Override
   public int getAggregateIndex() {
      return this.aggregateIndex;
   }

   @Override
   public @Nullable Object getSource() {
      return this.source;
   }

   @Override
   public @Nullable MergedAnnotation<?> getMetaSource() {
      AnnotationTypeMapping metaSourceMapping = this.mapping.getSource();
      return metaSourceMapping == null
         ? null
         : new TypeMappedAnnotation(
            metaSourceMapping, this.classLoader, this.source, this.rootAttributes, this.valueExtractor, this.aggregateIndex, this.resolvedRootMirrors
         );
   }

   @Override
   public MergedAnnotation<?> getRoot() {
      if (this.getDistance() == 0) {
         return this;
      }

      AnnotationTypeMapping rootMapping = this.mapping.getRoot();
      return new TypeMappedAnnotation(
         rootMapping, this.classLoader, this.source, this.rootAttributes, this.valueExtractor, this.aggregateIndex, this.resolvedRootMirrors
      );
   }

   @Override
   public boolean hasDefaultValue(String attributeName) {
      int attributeIndex = this.getAttributeIndex(attributeName, true);
      Object value = this.getValue(attributeIndex, false);
      return value == null || this.mapping.isEquivalentToDefaultValue(attributeIndex, value, this.valueExtractor);
   }

   @Override
   public <T extends Annotation> MergedAnnotation<T> getAnnotation(String attributeName, Class<T> type) throws NoSuchElementException {
      int attributeIndex = this.getAttributeIndex(attributeName, true);
      Method attribute = this.mapping.getAttributes().get(attributeIndex);
      Assert.notNull(type, "Type must not be null");
      Assert.isAssignable(type, attribute.getReturnType(), () -> "Attribute " + attributeName + " type mismatch:");
      return (MergedAnnotation<T>)this.getRequiredValue(attributeIndex, attributeName);
   }

   @Override
   public <T extends Annotation> MergedAnnotation<T>[] getAnnotationArray(String attributeName, Class<T> type) throws NoSuchElementException {
      int attributeIndex = this.getAttributeIndex(attributeName, true);
      Method attribute = this.mapping.getAttributes().get(attributeIndex);
      Class<?> componentType = attribute.getReturnType().componentType();
      Assert.notNull(type, "Type must not be null");
      Assert.notNull(componentType, () -> "Attribute " + attributeName + " is not an array");
      Assert.isAssignable(type, componentType, () -> "Attribute " + attributeName + " component type mismatch:");
      return (MergedAnnotation<T>[])this.getRequiredValue(attributeIndex, attributeName);
   }

   @Override
   public <T> Optional<T> getDefaultValue(String attributeName, Class<T> type) {
      int attributeIndex = this.getAttributeIndex(attributeName, false);
      if (attributeIndex == -1) {
         return Optional.empty();
      }

      Method attribute = this.mapping.getAttributes().get(attributeIndex);
      return Optional.ofNullable(this.adapt(attribute, attribute.getDefaultValue(), type));
   }

   @Override
   public MergedAnnotation<A> filterAttributes(Predicate<String> predicate) {
      if (this.attributeFilter != null) {
         predicate = this.attributeFilter.and(predicate);
      }

      return new TypeMappedAnnotation<>(
         this.mapping,
         this.classLoader,
         this.source,
         this.rootAttributes,
         this.valueExtractor,
         this.aggregateIndex,
         this.useMergedValues,
         predicate,
         this.resolvedRootMirrors,
         this.resolvedMirrors
      );
   }

   @Override
   public MergedAnnotation<A> withNonMergedAttributes() {
      return new TypeMappedAnnotation<>(
         this.mapping,
         this.classLoader,
         this.source,
         this.rootAttributes,
         this.valueExtractor,
         this.aggregateIndex,
         false,
         this.attributeFilter,
         this.resolvedRootMirrors,
         this.resolvedMirrors
      );
   }

   @Override
   public Map<String, Object> asMap(MergedAnnotation.Adapt... adaptations) {
      return Collections.unmodifiableMap(this.asMap(mergedAnnotation -> new LinkedHashMap<>(), adaptations));
   }

   @Override
   public <T extends Map<String, Object>> T asMap(Function<MergedAnnotation<?>, T> factory, MergedAnnotation.Adapt... adaptations) {
      T map = (T)factory.apply(this);
      Assert.state(map != null, "Factory used to create MergedAnnotation Map must not return null");
      AttributeMethods attributes = this.mapping.getAttributes();

      for (int i = 0; i < attributes.size(); i++) {
         Method attribute = attributes.get(i);
         if (!this.isFiltered(attribute.getName())) {
            Object value;
            try {
               value = this.getValue(i, this.getTypeForMapOptions(attribute, adaptations));
            } catch (Throwable ex) {
               map.put(attribute.getName(), ex);
               continue;
            }

            if (value != null) {
               map.put(attribute.getName(), this.adaptValueForMapOptions(attribute, value, map.getClass(), factory, adaptations));
            }
         }
      }

      return map;
   }

   private Class<?> getTypeForMapOptions(Method attribute, MergedAnnotation.Adapt[] adaptations) {
      Class<?> attributeType = attribute.getReturnType();
      Class<?> componentType = attributeType.isArray() ? attributeType.componentType() : attributeType;
      if (MergedAnnotation.Adapt.CLASS_TO_STRING.isIn(adaptations) && componentType == Class.class) {
         return attributeType.isArray() ? String[].class : String.class;
      } else {
         return Object.class;
      }
   }

   private <T extends Map<String, Object>> Object adaptValueForMapOptions(
      Method attribute, Object value, Class<?> mapType, Function<MergedAnnotation<?>, T> factory, MergedAnnotation.Adapt[] adaptations
   ) {
      if (value instanceof MergedAnnotation<?> annotation) {
         return MergedAnnotation.Adapt.ANNOTATION_TO_MAP.isIn(adaptations) ? annotation.asMap(factory, adaptations) : annotation.synthesize();
      } else if (!(value instanceof MergedAnnotation<?>[] annotations)) {
         return value;
      } else if (MergedAnnotation.Adapt.ANNOTATION_TO_MAP.isIn(adaptations)) {
         Object result = Array.newInstance(mapType, annotations.length);

         for (int i = 0; i < annotations.length; i++) {
            Array.set(result, i, annotations[i].asMap(factory, adaptations));
         }

         return result;
      } else {
         Object result = Array.newInstance(attribute.getReturnType().componentType(), annotations.length);

         for (int i = 0; i < annotations.length; i++) {
            Array.set(result, i, annotations[i].synthesize());
         }

         return result;
      }
   }

   @Override
   protected A createSynthesizedAnnotation() {
      if (this.rootAttributes instanceof Annotation ann && this.isTargetAnnotation(ann) && !this.isSynthesizable(ann)) {
         return (A)ann;
      } else {
         Annotation meta = this.mapping.getAnnotation();
         return (A)(meta != null && this.isTargetAnnotation(meta) && !this.isSynthesizable(meta)
            ? meta
            : SynthesizedMergedAnnotationInvocationHandler.createProxy(this, this.getType()));
      }
   }

   private boolean isTargetAnnotation(Object obj) {
      return this.getType().isInstance(obj);
   }

   private boolean isSynthesizable(Annotation annotation) {
      return AnnotationUtils.isSynthesizedAnnotation(annotation) ? false : this.mapping.isSynthesizable();
   }

   @Override
   protected <T> @Nullable T getAttributeValue(String attributeName, Class<T> type) {
      int attributeIndex = this.getAttributeIndex(attributeName, false);
      return attributeIndex != -1 ? this.getValue(attributeIndex, type) : null;
   }

   private Object getRequiredValue(int attributeIndex, String attributeName) {
      Object value = this.getValue(attributeIndex, Object.class);
      if (value == null) {
         throw new NoSuchElementException("No element at attribute index " + attributeIndex + " for name " + attributeName);
      } else {
         return value;
      }
   }

   private <T> @Nullable T getValue(int attributeIndex, Class<T> type) {
      Method attribute = this.mapping.getAttributes().get(attributeIndex);
      Object value = this.getValue(attributeIndex, false);
      if (value == null) {
         value = attribute.getDefaultValue();
      }

      return this.adapt(attribute, value, type);
   }

   private @Nullable Object getValue(int attributeIndex, boolean forMirrorResolution) {
      AnnotationTypeMapping mapping = this.mapping;
      if (this.useMergedValues) {
         int mappedIndex = this.mapping.getAliasMapping(attributeIndex);
         if (mappedIndex != -1) {
            mapping = mapping.getRoot();
            attributeIndex = mappedIndex;
         }
      }

      if (!forMirrorResolution) {
         attributeIndex = (mapping.getDistance() != 0 ? this.resolvedMirrors : this.resolvedRootMirrors)[attributeIndex];
      }

      if (attributeIndex == -1) {
         return null;
      } else if (mapping.getDistance() == 0) {
         Method attribute = mapping.getAttributes().get(attributeIndex);
         Object result = this.valueExtractor.extract(attribute, this.rootAttributes);
         return result != null ? result : attribute.getDefaultValue();
      } else {
         return this.getValueFromMetaAnnotation(attributeIndex, forMirrorResolution);
      }
   }

   private @Nullable Object getValueFromMetaAnnotation(int attributeIndex, boolean forMirrorResolution) {
      Object value = null;
      if (this.useMergedValues || forMirrorResolution) {
         value = this.mapping.getMappedAnnotationValue(attributeIndex, forMirrorResolution);
      }

      if (value == null) {
         Method attribute = this.mapping.getAttributes().get(attributeIndex);
         value = AnnotationUtils.invokeAnnotationMethod(attribute, this.mapping.getAnnotation());
      }

      return value;
   }

   private @Nullable Object getValueForMirrorResolution(Method attribute, @Nullable Object annotation) {
      int attributeIndex = this.mapping.getAttributes().indexOf(attribute);
      return this.getValue(attributeIndex, true);
   }

   private <T> @Nullable T adapt(Method attribute, @Nullable Object value, Class<T> type) {
      if (value == null) {
         return null;
      }

      value = this.adaptForAttribute(attribute, value);
      type = this.getAdaptType(attribute, type);
      if (value instanceof Class<?> clazz && type == String.class) {
         value = clazz.getName();
      } else if (value instanceof String str && type == Class.class) {
         try {
            value = ClassUtils.forName(str, this.getClassLoader());
         } catch (ClassNotFoundException | LinkageError ex) {
            throw new TypeNotPresentException(str, ex);
         }
      } else if (value instanceof Class<?>[] classes && type == String[].class) {
         String[] names = new String[classes.length];

         for (int i = 0; i < classes.length; i++) {
            names[i] = classes[i].getName();
         }

         value = names;
      } else if (value instanceof String[] names && type == Class[].class) {
         Class<?>[] classes = new Class[names.length];
         ClassLoader classLoader = this.getClassLoader();

         for (int i = 0; i < names.length; i++) {
            try {
               classes[i] = ClassUtils.forName(names[i], classLoader);
            } catch (ClassNotFoundException | LinkageError ex) {
               throw new TypeNotPresentException(names[i], ex);
            }
         }

         value = classes;
      } else if (value instanceof MergedAnnotation<?> annotation && type.isAnnotation()) {
         value = annotation.synthesize();
      } else if (value instanceof MergedAnnotation<?>[] annotations && type.isArray() && type.componentType().isAnnotation()) {
         Object array = Array.newInstance(type.componentType(), annotations.length);

         for (int i = 0; i < annotations.length; i++) {
            Array.set(array, i, annotations[i].synthesize());
         }

         value = array;
      }

      if (!type.isInstance(value)) {
         throw new IllegalArgumentException(
            "Unable to adapt value of type " + ClassUtils.getCanonicalName(value.getClass()) + " to " + ClassUtils.getCanonicalName(type)
         );
      } else {
         return (T)value;
      }
   }

   private Object adaptForAttribute(Method attribute, Object value) {
      Class<?> attributeType = ClassUtils.resolvePrimitiveIfNecessary(attribute.getReturnType());
      if (attributeType.isArray() && !value.getClass().isArray()) {
         Object array = Array.newInstance(value.getClass(), 1);
         Array.set(array, 0, value);
         return this.adaptForAttribute(attribute, array);
      }

      if (attributeType.isAnnotation()) {
         return this.adaptToMergedAnnotation(value, (Class<? extends Annotation>)attributeType);
      }

      if (attributeType.isArray() && attributeType.componentType().isAnnotation()) {
         MergedAnnotation<?>[] result = new MergedAnnotation[Array.getLength(value)];

         for (int i = 0; i < result.length; i++) {
            result[i] = this.adaptToMergedAnnotation(Array.get(value, i), (Class<? extends Annotation>)attributeType.componentType());
         }

         return result;
      } else if ((attributeType != Class.class || !(value instanceof String))
         && (attributeType != Class[].class || !(value instanceof String[]))
         && (attributeType != String.class || !(value instanceof Class))
         && (attributeType != String[].class || !(value instanceof Class[]))) {
         if (attributeType.isArray() && this.isEmptyObjectArray(value)) {
            return this.emptyArray(attributeType.componentType());
         } else if (!attributeType.isInstance(value)) {
            throw new IllegalStateException(
               "Attribute '"
                  + attribute.getName()
                  + "' in annotation "
                  + ClassUtils.getCanonicalName(this.getType())
                  + " should be compatible with "
                  + ClassUtils.getCanonicalName(attributeType)
                  + " but a "
                  + ClassUtils.getCanonicalName(value.getClass())
                  + " value was returned"
            );
         } else {
            return value;
         }
      } else {
         return value;
      }
   }

   private boolean isEmptyObjectArray(Object value) {
      return value instanceof Object[] objects && objects.length == 0;
   }

   private Object emptyArray(Class<?> componentType) {
      Object result = EMPTY_ARRAYS.get(componentType);
      if (result == null) {
         result = Array.newInstance(componentType, 0);
      }

      return result;
   }

   private MergedAnnotation<?> adaptToMergedAnnotation(Object value, Class<? extends Annotation> annotationType) {
      if (value instanceof MergedAnnotation<?> mergedAnnotation) {
         return mergedAnnotation;
      } else {
         AnnotationTypeMapping mapping = AnnotationTypeMappings.forAnnotationType(annotationType).get(0);
         return new TypeMappedAnnotation(mapping, null, this.source, value, this.getValueExtractor(value), this.aggregateIndex);
      }
   }

   private ValueExtractor getValueExtractor(Object value) {
      if (value instanceof Annotation) {
         return AnnotationUtils::invokeAnnotationMethod;
      } else {
         return value instanceof Map ? TypeMappedAnnotation::extractFromMap : this.valueExtractor;
      }
   }

   private <T> Class<T> getAdaptType(Method attribute, Class<T> type) {
      if (type != Object.class) {
         return type;
      } else {
         Class<?> attributeType = attribute.getReturnType();
         if (attributeType.isAnnotation()) {
            return (Class<T>)MergedAnnotation.class;
         } else {
            return (Class<T>)(attributeType.isArray() && attributeType.componentType().isAnnotation()
               ? MergedAnnotation[].class
               : ClassUtils.resolvePrimitiveIfNecessary(attributeType));
         }
      }
   }

   private int getAttributeIndex(String attributeName, boolean required) {
      Assert.hasText(attributeName, "Attribute name must not be null");
      int attributeIndex = this.isFiltered(attributeName) ? -1 : this.mapping.getAttributes().indexOf(attributeName);
      if (attributeIndex == -1 && required) {
         throw new NoSuchElementException(
            "No attribute named '" + attributeName + "' present in merged annotation " + ClassUtils.getCanonicalName(this.getType())
         );
      } else {
         return attributeIndex;
      }
   }

   private boolean isFiltered(String attributeName) {
      return this.attributeFilter != null ? !this.attributeFilter.test(attributeName) : false;
   }

   private @Nullable ClassLoader getClassLoader() {
      if (this.classLoader != null) {
         return this.classLoader;
      }

      if (this.source != null) {
         if (this.source instanceof Class<?> clazz) {
            return clazz.getClassLoader();
         }

         if (this.source instanceof Member member) {
            return member.getDeclaringClass().getClassLoader();
         }
      }

      return null;
   }

   static <A extends Annotation> MergedAnnotation<A> from(@Nullable Object source, A annotation) {
      Assert.notNull(annotation, "Annotation must not be null");
      AnnotationTypeMappings mappings = AnnotationTypeMappings.forAnnotationType(annotation.annotationType());
      return new TypeMappedAnnotation<>(mappings.get(0), null, source, annotation, AnnotationUtils::invokeAnnotationMethod, 0);
   }

   static <A extends Annotation> MergedAnnotation<A> of(
      @Nullable ClassLoader classLoader, @Nullable Object source, Class<A> annotationType, @Nullable Map<String, ?> attributes
   ) {
      Assert.notNull(annotationType, "Annotation type must not be null");
      AnnotationTypeMappings mappings = AnnotationTypeMappings.forAnnotationType(annotationType);
      return new TypeMappedAnnotation<>(mappings.get(0), classLoader, source, attributes, TypeMappedAnnotation::extractFromMap, 0);
   }

   static <A extends Annotation> @Nullable TypeMappedAnnotation<A> createIfPossible(
      AnnotationTypeMapping mapping, MergedAnnotation<?> annotation, IntrospectionFailureLogger logger
   ) {
      return annotation instanceof TypeMappedAnnotation<?> typeMappedAnnotation
         ? createIfPossible(
            mapping,
            typeMappedAnnotation.source,
            typeMappedAnnotation.rootAttributes,
            typeMappedAnnotation.valueExtractor,
            typeMappedAnnotation.aggregateIndex,
            logger
         )
         : createIfPossible(mapping, annotation.getSource(), annotation.synthesize(), annotation.getAggregateIndex(), logger);
   }

   static <A extends Annotation> @Nullable TypeMappedAnnotation<A> createIfPossible(
      AnnotationTypeMapping mapping, @Nullable Object source, Annotation annotation, int aggregateIndex, IntrospectionFailureLogger logger
   ) {
      return createIfPossible(mapping, source, annotation, AnnotationUtils::invokeAnnotationMethod, aggregateIndex, logger);
   }

   private static <A extends Annotation> @Nullable TypeMappedAnnotation<A> createIfPossible(
      AnnotationTypeMapping mapping,
      @Nullable Object source,
      @Nullable Object rootAttribute,
      ValueExtractor valueExtractor,
      int aggregateIndex,
      IntrospectionFailureLogger logger
   ) {
      try {
         return new TypeMappedAnnotation<>(mapping, null, source, rootAttribute, valueExtractor, aggregateIndex);
      } catch (Exception ex) {
         AnnotationUtils.rethrowAnnotationConfigurationException(ex);
         if (logger.isEnabled()) {
            String type = ClassUtils.getCanonicalName(mapping.getAnnotationType());
            String item = mapping.getDistance() == 0
               ? "annotation " + type
               : "meta-annotation " + type + " from " + ClassUtils.getCanonicalName(mapping.getRoot().getAnnotationType());
            logger.log("Failed to introspect " + item, source, ex);
         }

         return null;
      }
   }

   static @Nullable Object extractFromMap(Method attribute, @Nullable Object map) {
      return map != null ? ((Map)map).get(attribute.getName()) : null;
   }
}
