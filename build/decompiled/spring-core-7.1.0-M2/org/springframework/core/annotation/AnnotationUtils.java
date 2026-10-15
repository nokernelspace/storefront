package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Map.Entry;
import org.jspecify.annotations.Nullable;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

public abstract class AnnotationUtils {
   public static final String VALUE = "value";
   private static final AnnotationFilter JAVA_LANG_ANNOTATION_FILTER = AnnotationFilter.packages("java.lang.annotation");
   private static final Map<Class<? extends Annotation>, Map<String, AnnotationUtils.DefaultValueHolder>> defaultValuesCache = new ConcurrentReferenceHashMap<>();

   public static boolean isCandidateClass(Class<?> clazz, Collection<Class<? extends Annotation>> annotationTypes) {
      for (Class<? extends Annotation> annotationType : annotationTypes) {
         if (isCandidateClass(clazz, annotationType)) {
            return true;
         }
      }

      return false;
   }

   public static boolean isCandidateClass(Class<?> clazz, @Nullable Class<? extends Annotation> annotationType) {
      return annotationType != null && isCandidateClass(clazz, annotationType.getName());
   }

   public static boolean isCandidateClass(Class<?> clazz, String annotationName) {
      return annotationName.startsWith("java.") ? true : !AnnotationsScanner.hasPlainJavaAnnotationsOnly(clazz);
   }

   public static @Nullable Class<? extends Annotation> loadAnnotationType(String annotationName) {
      try {
         return (Class<? extends Annotation>)ClassUtils.forName(annotationName, AnnotationUtils.class.getClassLoader());
      } catch (ClassNotFoundException ex) {
         return null;
      }
   }

   public static <A extends Annotation> @Nullable A getAnnotation(Annotation annotation, Class<A> annotationType) {
      if (annotationType.isInstance(annotation)) {
         return synthesizeAnnotation((A)annotation, annotationType);
      } else {
         return AnnotationsScanner.hasPlainJavaAnnotationsOnly(annotation)
            ? null
            : MergedAnnotations.from(annotation, new Annotation[]{annotation}, RepeatableContainers.none())
               .get(annotationType)
               .withNonMergedAttributes()
               .synthesize(AnnotationUtils::isSingleLevelPresent)
               .orElse(null);
      }
   }

   public static <A extends Annotation> @Nullable A getAnnotation(AnnotatedElement annotatedElement, Class<A> annotationType) {
      return !AnnotationFilter.PLAIN.matches(annotationType) && !AnnotationsScanner.hasPlainJavaAnnotationsOnly(annotatedElement)
         ? MergedAnnotations.from(annotatedElement, MergedAnnotations.SearchStrategy.INHERITED_ANNOTATIONS, RepeatableContainers.none())
            .get(annotationType)
            .withNonMergedAttributes()
            .synthesize(AnnotationUtils::isSingleLevelPresent)
            .orElse(null)
         : annotatedElement.getAnnotation(annotationType);
   }

   private static <A extends Annotation> boolean isSingleLevelPresent(MergedAnnotation<A> mergedAnnotation) {
      int distance = mergedAnnotation.getDistance();
      return distance == 0 || distance == 1;
   }

   public static <A extends Annotation> @Nullable A getAnnotation(Method method, Class<A> annotationType) {
      Method resolvedMethod = BridgeMethodResolver.findBridgedMethod(method);
      return getAnnotation((AnnotatedElement)resolvedMethod, annotationType);
   }

   @Deprecated(since = "5.2")
   public static Annotation @Nullable [] getAnnotations(AnnotatedElement annotatedElement) {
      try {
         return synthesizeAnnotationArray(annotatedElement.getAnnotations(), annotatedElement);
      } catch (Throwable ex) {
         handleIntrospectionFailure(annotatedElement, ex);
         return null;
      }
   }

   @Deprecated(since = "5.2")
   public static Annotation @Nullable [] getAnnotations(Method method) {
      try {
         return synthesizeAnnotationArray(BridgeMethodResolver.findBridgedMethod(method).getAnnotations(), method);
      } catch (Throwable ex) {
         handleIntrospectionFailure(method, ex);
         return null;
      }
   }

   @Deprecated(since = "5.2")
   public static <A extends Annotation> Set<A> getRepeatableAnnotations(AnnotatedElement annotatedElement, Class<A> annotationType) {
      return getRepeatableAnnotations(annotatedElement, annotationType, null);
   }

   @Deprecated(since = "5.2")
   public static <A extends Annotation> Set<A> getRepeatableAnnotations(
      AnnotatedElement annotatedElement, Class<A> annotationType, @Nullable Class<? extends Annotation> containerAnnotationType
   ) {
      RepeatableContainers repeatableContainers = containerAnnotationType != null
         ? RepeatableContainers.explicitRepeatable(annotationType, containerAnnotationType)
         : RepeatableContainers.standardRepeatables();
      return MergedAnnotations.from(annotatedElement, MergedAnnotations.SearchStrategy.SUPERCLASS, repeatableContainers)
         .stream(annotationType)
         .filter(MergedAnnotationPredicates.firstRunOf(MergedAnnotation::getAggregateIndex))
         .map(MergedAnnotation::withNonMergedAttributes)
         .collect(MergedAnnotationCollectors.toAnnotationSet());
   }

   @Deprecated(since = "5.2")
   public static <A extends Annotation> Set<A> getDeclaredRepeatableAnnotations(AnnotatedElement annotatedElement, Class<A> annotationType) {
      return getDeclaredRepeatableAnnotations(annotatedElement, annotationType, null);
   }

   @Deprecated(since = "5.2")
   public static <A extends Annotation> Set<A> getDeclaredRepeatableAnnotations(
      AnnotatedElement annotatedElement, Class<A> annotationType, @Nullable Class<? extends Annotation> containerAnnotationType
   ) {
      RepeatableContainers repeatableContainers = containerAnnotationType != null
         ? RepeatableContainers.explicitRepeatable(annotationType, containerAnnotationType)
         : RepeatableContainers.standardRepeatables();
      return MergedAnnotations.from(annotatedElement, MergedAnnotations.SearchStrategy.DIRECT, repeatableContainers)
         .stream(annotationType)
         .map(MergedAnnotation::withNonMergedAttributes)
         .collect(MergedAnnotationCollectors.toAnnotationSet());
   }

   public static <A extends Annotation> @Nullable A findAnnotation(AnnotatedElement annotatedElement, @Nullable Class<A> annotationType) {
      if (annotationType == null) {
         return null;
      } else {
         return !AnnotationFilter.PLAIN.matches(annotationType) && !AnnotationsScanner.hasPlainJavaAnnotationsOnly(annotatedElement)
            ? MergedAnnotations.from(annotatedElement, MergedAnnotations.SearchStrategy.INHERITED_ANNOTATIONS, RepeatableContainers.none())
               .get(annotationType)
               .withNonMergedAttributes()
               .synthesize(MergedAnnotation::isPresent)
               .orElse(null)
            : annotatedElement.getDeclaredAnnotation(annotationType);
      }
   }

   public static <A extends Annotation> @Nullable A findAnnotation(Method method, @Nullable Class<A> annotationType) {
      if (annotationType == null) {
         return null;
      } else {
         return !AnnotationFilter.PLAIN.matches(annotationType) && !AnnotationsScanner.hasPlainJavaAnnotationsOnly(method)
            ? MergedAnnotations.from(method, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY, RepeatableContainers.none())
               .get(annotationType)
               .withNonMergedAttributes()
               .synthesize(MergedAnnotation::isPresent)
               .orElse(null)
            : method.getDeclaredAnnotation(annotationType);
      }
   }

   public static <A extends Annotation> @Nullable A findAnnotation(Class<?> clazz, @Nullable Class<A> annotationType) {
      if (annotationType == null) {
         return null;
      }

      if (!AnnotationFilter.PLAIN.matches(annotationType) && !AnnotationsScanner.hasPlainJavaAnnotationsOnly(clazz)) {
         return MergedAnnotations.from(clazz, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY, RepeatableContainers.none())
            .get(annotationType)
            .withNonMergedAttributes()
            .synthesize(MergedAnnotation::isPresent)
            .orElse(null);
      }

      A annotation = clazz.getDeclaredAnnotation(annotationType);
      if (annotation != null) {
         return annotation;
      }

      Class<?> superclass = clazz.getSuperclass();
      return superclass != null && superclass != Object.class ? findAnnotation(superclass, annotationType) : null;
   }

   @Deprecated(since = "5.2")
   public static @Nullable Class<?> findAnnotationDeclaringClass(Class<? extends Annotation> annotationType, @Nullable Class<?> clazz) {
      return clazz == null
         ? null
         : (Class)MergedAnnotations.from(clazz, MergedAnnotations.SearchStrategy.SUPERCLASS)
            .get(annotationType, MergedAnnotation::isDirectlyPresent)
            .getSource();
   }

   @Deprecated(since = "5.2")
   public static @Nullable Class<?> findAnnotationDeclaringClassForTypes(List<Class<? extends Annotation>> annotationTypes, @Nullable Class<?> clazz) {
      if (clazz == null) {
         return null;
      }

      MergedAnnotation<?> merged = MergedAnnotations.from(clazz, MergedAnnotations.SearchStrategy.SUPERCLASS)
         .stream()
         .filter(MergedAnnotationPredicates.<Annotation>typeIn(annotationTypes).and(MergedAnnotation::isDirectlyPresent))
         .findFirst()
         .orElse(null);
      return merged != null && merged.getSource() instanceof Class<?> sourceClass ? sourceClass : null;
   }

   public static boolean isAnnotationDeclaredLocally(Class<? extends Annotation> annotationType, Class<?> clazz) {
      return MergedAnnotations.from(clazz).get(annotationType).isDirectlyPresent();
   }

   @Deprecated(since = "5.2")
   public static boolean isAnnotationInherited(Class<? extends Annotation> annotationType, Class<?> clazz) {
      return MergedAnnotations.from(clazz, MergedAnnotations.SearchStrategy.INHERITED_ANNOTATIONS)
            .stream(annotationType)
            .filter(MergedAnnotation::isDirectlyPresent)
            .findFirst()
            .orElseGet(MergedAnnotation::missing)
            .getAggregateIndex()
         > 0;
   }

   @Deprecated(since = "5.2")
   public static boolean isAnnotationMetaPresent(Class<? extends Annotation> annotationType, @Nullable Class<? extends Annotation> metaAnnotationType) {
      if (metaAnnotationType == null) {
         return false;
      } else {
         return !AnnotationFilter.PLAIN.matches(metaAnnotationType) && !AnnotationsScanner.hasPlainJavaAnnotationsOnly(annotationType)
            ? MergedAnnotations.from(annotationType, MergedAnnotations.SearchStrategy.INHERITED_ANNOTATIONS, RepeatableContainers.none())
               .isPresent(metaAnnotationType)
            : annotationType.isAnnotationPresent(metaAnnotationType);
      }
   }

   public static boolean isInJavaLangAnnotationPackage(@Nullable Annotation annotation) {
      return annotation != null && JAVA_LANG_ANNOTATION_FILTER.matches(annotation);
   }

   public static boolean isInJavaLangAnnotationPackage(@Nullable String annotationType) {
      return annotationType != null && JAVA_LANG_ANNOTATION_FILTER.matches(annotationType);
   }

   public static void validateAnnotation(Annotation annotation) {
      AttributeMethods.forAnnotationType(annotation.annotationType()).validate(annotation);
   }

   public static Map<String, @Nullable Object> getAnnotationAttributes(Annotation annotation) {
      return getAnnotationAttributes(null, annotation);
   }

   public static Map<String, @Nullable Object> getAnnotationAttributes(Annotation annotation, boolean classValuesAsString) {
      return getAnnotationAttributes(annotation, classValuesAsString, false);
   }

   public static AnnotationAttributes getAnnotationAttributes(Annotation annotation, boolean classValuesAsString, boolean nestedAnnotationsAsMap) {
      return getAnnotationAttributes(null, annotation, classValuesAsString, nestedAnnotationsAsMap);
   }

   public static AnnotationAttributes getAnnotationAttributes(@Nullable AnnotatedElement annotatedElement, Annotation annotation) {
      return getAnnotationAttributes(annotatedElement, annotation, false, false);
   }

   public static AnnotationAttributes getAnnotationAttributes(
      @Nullable AnnotatedElement annotatedElement, Annotation annotation, boolean classValuesAsString, boolean nestedAnnotationsAsMap
   ) {
      MergedAnnotation.Adapt[] adaptations = MergedAnnotation.Adapt.values(classValuesAsString, nestedAnnotationsAsMap);
      return MergedAnnotation.from(annotatedElement, annotation)
         .withNonMergedAttributes()
         .asMap(mergedAnnotation -> new AnnotationAttributes((Class<? extends Annotation>)mergedAnnotation.getType(), true), adaptations);
   }

   public static void registerDefaultValues(AnnotationAttributes attributes) {
      Class<? extends Annotation> annotationType = attributes.annotationType();
      if (annotationType != null && Modifier.isPublic(annotationType.getModifiers()) && !AnnotationFilter.PLAIN.matches(annotationType)) {
         Map<String, AnnotationUtils.DefaultValueHolder> defaultValues = getDefaultValues(annotationType);
         defaultValues.forEach(attributes::putIfAbsent);
      }
   }

   private static Map<String, AnnotationUtils.DefaultValueHolder> getDefaultValues(Class<? extends Annotation> annotationType) {
      return defaultValuesCache.computeIfAbsent(annotationType, AnnotationUtils::computeDefaultValues);
   }

   private static Map<String, AnnotationUtils.DefaultValueHolder> computeDefaultValues(Class<? extends Annotation> annotationType) {
      AttributeMethods methods = AttributeMethods.forAnnotationType(annotationType);
      if (!methods.hasDefaultValueMethod()) {
         return Collections.emptyMap();
      }

      Map<String, AnnotationUtils.DefaultValueHolder> result = CollectionUtils.newLinkedHashMap(methods.size());
      if (!methods.hasNestedAnnotation()) {
         for (int i = 0; i < methods.size(); i++) {
            Method method = methods.get(i);
            Object defaultValue = method.getDefaultValue();
            if (defaultValue != null) {
               result.put(method.getName(), new AnnotationUtils.DefaultValueHolder(defaultValue));
            }
         }
      } else {
         AnnotationAttributes attributes = MergedAnnotation.of(annotationType)
            .asMap(annotation -> new AnnotationAttributes((Class<? extends Annotation>)annotation.getType(), true), MergedAnnotation.Adapt.ANNOTATION_TO_MAP);

         for (Entry<String, Object> element : attributes.entrySet()) {
            result.put(element.getKey(), new AnnotationUtils.DefaultValueHolder(element.getValue()));
         }
      }

      return result;
   }

   public static void postProcessAnnotationAttributes(@Nullable Object annotatedElement, @Nullable AnnotationAttributes attributes, boolean classValuesAsString) {
      if (attributes != null) {
         if (!attributes.validated) {
            Class<? extends Annotation> annotationType = attributes.annotationType();
            if (annotationType == null) {
               return;
            }

            AnnotationTypeMapping mapping = AnnotationTypeMappings.forAnnotationType(annotationType).get(0);

            for (int i = 0; i < mapping.getMirrorSets().size(); i++) {
               AnnotationTypeMapping.MirrorSets.MirrorSet mirrorSet = mapping.getMirrorSets().get(i);
               int resolved = mirrorSet.resolve(attributes.displayName, attributes, AnnotationUtils::getAttributeValueForMirrorResolution);
               if (resolved != -1) {
                  Method attribute = mapping.getAttributes().get(resolved);
                  Object value = attributes.get(attribute.getName());

                  for (int j = 0; j < mirrorSet.size(); j++) {
                     Method mirror = mirrorSet.get(j);
                     if (mirror != attribute) {
                        attributes.put(mirror.getName(), adaptValue(annotatedElement, value, classValuesAsString));
                     }
                  }
               }
            }
         }

         for (Entry<String, Object> attributeEntry : attributes.entrySet()) {
            String attributeName = attributeEntry.getKey();
            if (attributeEntry.getValue() instanceof AnnotationUtils.DefaultValueHolder defaultValueHolder) {
               Object var16 = defaultValueHolder.defaultValue;
               attributes.put(attributeName, adaptValue(annotatedElement, var16, classValuesAsString));
            }
         }
      }
   }

   private static @Nullable Object getAttributeValueForMirrorResolution(Method attribute, @Nullable Object attributes) {
      if (attributes instanceof AnnotationAttributes annotationAttributes) {
         Object result = annotationAttributes.get(attribute.getName());
         return result instanceof AnnotationUtils.DefaultValueHolder defaultValueHolder ? defaultValueHolder.defaultValue : result;
      } else {
         return null;
      }
   }

   private static @Nullable Object adaptValue(@Nullable Object annotatedElement, @Nullable Object value, boolean classValuesAsString) {
      if (classValuesAsString) {
         if (value instanceof Class<?> clazz) {
            return clazz.getName();
         }

         if (value instanceof Class<?>[] classes) {
            String[] names = new String[classes.length];

            for (int i = 0; i < classes.length; i++) {
               names[i] = classes[i].getName();
            }

            return names;
         }
      }

      if (value instanceof Annotation annotation) {
         return MergedAnnotation.from(annotatedElement, annotation).synthesize();
      } else if (!(value instanceof Annotation[] annotations)) {
         return value;
      } else {
         Annotation[] synthesized = (Annotation[])Array.newInstance(annotations.getClass().componentType(), annotations.length);

         for (int i = 0; i < annotations.length; i++) {
            synthesized[i] = MergedAnnotation.from(annotatedElement, annotations[i]).synthesize();
         }

         return synthesized;
      }
   }

   public static @Nullable Object getValue(Annotation annotation) {
      return getValue(annotation, "value");
   }

   public static @Nullable Object getValue(@Nullable Annotation annotation, @Nullable String attributeName) {
      if (annotation != null && StringUtils.hasText(attributeName)) {
         try {
            for (Method method : annotation.annotationType().getDeclaredMethods()) {
               if (method.getName().equals(attributeName) && method.getParameterCount() == 0) {
                  return invokeAnnotationMethod(method, annotation);
               }
            }
         } catch (Throwable ex) {
            handleValueRetrievalFailure(annotation, ex);
         }

         return null;
      } else {
         return null;
      }
   }

   static @Nullable Object invokeAnnotationMethod(Method method, @Nullable Object annotation) {
      if (annotation == null) {
         return null;
      }

      if (Proxy.isProxyClass(annotation.getClass())) {
         try {
            InvocationHandler handler = Proxy.getInvocationHandler(annotation);
            return handler.invoke(annotation, method, null);
         } catch (Throwable var3) {
         }
      }

      return ReflectionUtils.invokeMethod(method, annotation);
   }

   static void rethrowAnnotationConfigurationException(Throwable ex) {
      if (ex instanceof AnnotationConfigurationException exception) {
         throw exception;
      }
   }

   static void handleIntrospectionFailure(AnnotatedElement element, Throwable ex) {
      rethrowAnnotationConfigurationException(ex);
      IntrospectionFailureLogger logger = IntrospectionFailureLogger.INFO;
      boolean meta = false;
      if (element instanceof Class<?> clazz && Annotation.class.isAssignableFrom(clazz)) {
         logger = IntrospectionFailureLogger.DEBUG;
         meta = true;
      }

      if (logger.isEnabled()) {
         logger.log("Failed to " + (meta ? "meta-introspect annotation " : "introspect annotations on ") + element + ": " + ex);
      }
   }

   private static void handleValueRetrievalFailure(Annotation annotation, Throwable ex) {
      rethrowAnnotationConfigurationException(ex);
      IntrospectionFailureLogger logger = IntrospectionFailureLogger.INFO;
      if (logger.isEnabled()) {
         logger.log("Failed to retrieve value from " + annotation + ": " + ex);
      }
   }

   public static @Nullable Object getDefaultValue(Annotation annotation) {
      return getDefaultValue(annotation, "value");
   }

   public static @Nullable Object getDefaultValue(@Nullable Annotation annotation, @Nullable String attributeName) {
      return annotation != null ? getDefaultValue(annotation.annotationType(), attributeName) : null;
   }

   public static @Nullable Object getDefaultValue(Class<? extends Annotation> annotationType) {
      return getDefaultValue(annotationType, "value");
   }

   public static @Nullable Object getDefaultValue(@Nullable Class<? extends Annotation> annotationType, @Nullable String attributeName) {
      return annotationType != null && StringUtils.hasText(attributeName)
         ? MergedAnnotation.of(annotationType).getDefaultValue(attributeName).orElse(null)
         : null;
   }

   public static <A extends Annotation> A synthesizeAnnotation(A annotation, @Nullable AnnotatedElement annotatedElement) {
      return !isSynthesizedAnnotation(annotation) && !AnnotationFilter.PLAIN.matches(annotation)
         ? MergedAnnotation.<A>from(annotatedElement, annotation).synthesize()
         : annotation;
   }

   public static <A extends Annotation> A synthesizeAnnotation(Class<A> annotationType) {
      return synthesizeAnnotation(Collections.emptyMap(), annotationType, null);
   }

   public static <A extends Annotation> A synthesizeAnnotation(
      Map<String, Object> attributes, Class<A> annotationType, @Nullable AnnotatedElement annotatedElement
   ) {
      try {
         return MergedAnnotation.of(annotatedElement, annotationType, attributes).synthesize();
      } catch (NoSuchElementException | IllegalStateException ex) {
         throw new IllegalArgumentException(ex);
      }
   }

   static Annotation[] synthesizeAnnotationArray(Annotation[] annotations, AnnotatedElement annotatedElement) {
      if (AnnotationsScanner.hasPlainJavaAnnotationsOnly(annotatedElement)) {
         return annotations;
      }

      Annotation[] synthesized = (Annotation[])Array.newInstance(annotations.getClass().componentType(), annotations.length);

      for (int i = 0; i < annotations.length; i++) {
         synthesized[i] = synthesizeAnnotation(annotations[i], annotatedElement);
      }

      return synthesized;
   }

   public static boolean isSynthesizedAnnotation(@Nullable Annotation annotation) {
      try {
         return annotation != null
            && Proxy.isProxyClass(annotation.getClass())
            && Proxy.getInvocationHandler(annotation) instanceof SynthesizedMergedAnnotationInvocationHandler;
      } catch (SecurityException ex) {
         return false;
      }
   }

   public static void clearCache() {
      AnnotationTypeMappings.clearCache();
      AnnotationsScanner.clearCache();
      AttributeMethods.cache.clear();
      RepeatableContainers.cache.clear();
      OrderUtils.orderCache.clear();
   }

   private static class DefaultValueHolder {
      final Object defaultValue;

      public DefaultValueHolder(Object defaultValue) {
         this.defaultValue = defaultValue;
      }

      @Override
      public String toString() {
         return "*" + this.defaultValue;
      }
   }
}
