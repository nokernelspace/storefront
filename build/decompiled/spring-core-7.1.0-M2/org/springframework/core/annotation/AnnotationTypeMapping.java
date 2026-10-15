package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

final class AnnotationTypeMapping {
   private static final AnnotationTypeMapping.MirrorSets.MirrorSet[] EMPTY_MIRROR_SETS = new AnnotationTypeMapping.MirrorSets.MirrorSet[0];
   private static final int[] EMPTY_INT_ARRAY = new int[0];
   private final @Nullable AnnotationTypeMapping source;
   private final AnnotationTypeMapping root;
   private final int distance;
   private final Class<? extends Annotation> annotationType;
   private final List<Class<? extends Annotation>> metaTypes;
   private final @Nullable Annotation annotation;
   private final AttributeMethods attributes;
   private final AnnotationTypeMapping.MirrorSets mirrorSets;
   private final int[] aliasMappings;
   private final int[] annotationValueMappings;
   private final AnnotationTypeMapping[] annotationValueSource;
   private final Map<Method, List<Method>> aliasedBy;
   private final boolean synthesizable;
   private final Set<Method> claimedAliases = new HashSet<>();

   AnnotationTypeMapping(
      @Nullable AnnotationTypeMapping source,
      Class<? extends Annotation> annotationType,
      @Nullable Annotation annotation,
      Set<Class<? extends Annotation>> visitedAnnotationTypes
   ) {
      this.source = source;
      this.root = source != null ? source.getRoot() : this;
      this.distance = source == null ? 0 : source.getDistance() + 1;
      this.annotationType = annotationType;
      this.metaTypes = merge(source != null ? source.getMetaTypes() : null, annotationType);
      this.annotation = annotation;
      this.attributes = AttributeMethods.forAnnotationType(annotationType);
      this.mirrorSets = new AnnotationTypeMapping.MirrorSets();
      this.aliasMappings = filledIntArray(this.attributes.size());
      this.annotationValueMappings = filledIntArray(this.attributes.size());
      this.annotationValueSource = new AnnotationTypeMapping[this.attributes.size()];
      this.aliasedBy = this.resolveAliasedForTargets();
      this.processAliases();
      this.synthesizable = this.computeSynthesizableFlag(visitedAnnotationTypes);
   }

   private static <T> List<T> merge(@Nullable List<T> existing, T element) {
      if (existing == null) {
         return Collections.singletonList(element);
      }

      List<T> merged = new ArrayList<>(existing.size() + 1);
      merged.addAll(existing);
      merged.add(element);
      return Collections.unmodifiableList(merged);
   }

   private Map<Method, List<Method>> resolveAliasedForTargets() {
      Map<Method, List<Method>> aliasedBy = new HashMap<>();

      for (int i = 0; i < this.attributes.size(); i++) {
         Method attribute = this.attributes.get(i);
         AliasFor aliasFor = AnnotationsScanner.getDeclaredAnnotation(attribute, AliasFor.class);
         if (aliasFor != null) {
            Method target = this.resolveAliasTarget(attribute, aliasFor);
            aliasedBy.computeIfAbsent(target, key -> new ArrayList<>()).add(attribute);
         }
      }

      return Collections.unmodifiableMap(aliasedBy);
   }

   private Method resolveAliasTarget(Method attribute, AliasFor aliasFor) {
      return this.resolveAliasTarget(attribute, aliasFor, true);
   }

   private Method resolveAliasTarget(Method attribute, AliasFor aliasFor, boolean checkAliasPair) {
      if (StringUtils.hasText(aliasFor.value()) && StringUtils.hasText(aliasFor.attribute())) {
         throw new AnnotationConfigurationException(
            String.format(
               "In @AliasFor declared on %s, attribute 'attribute' and its alias 'value' are present with values of '%s' and '%s', but only one is permitted.",
               AttributeMethods.describe(attribute),
               aliasFor.attribute(),
               aliasFor.value()
            )
         );
      }

      Class<? extends Annotation> targetAnnotation = aliasFor.annotation();
      if (targetAnnotation == Annotation.class) {
         targetAnnotation = this.annotationType;
      }

      String targetAttributeName = aliasFor.attribute();
      if (!StringUtils.hasLength(targetAttributeName)) {
         targetAttributeName = aliasFor.value();
      }

      if (!StringUtils.hasLength(targetAttributeName)) {
         targetAttributeName = attribute.getName();
      }

      Method target = AttributeMethods.forAnnotationType(targetAnnotation).get(targetAttributeName);
      if (target == null) {
         if (targetAnnotation == this.annotationType) {
            throw new AnnotationConfigurationException(
               String.format(
                  "@AliasFor declaration on %s declares an alias for '%s' which is not present.", AttributeMethods.describe(attribute), targetAttributeName
               )
            );
         } else {
            throw new AnnotationConfigurationException(
               String.format(
                  "%s is declared as an @AliasFor nonexistent %s.",
                  StringUtils.capitalize(AttributeMethods.describe(attribute)),
                  AttributeMethods.describe(targetAnnotation, targetAttributeName)
               )
            );
         }
      } else {
         if (target.equals(attribute)) {
            throw new AnnotationConfigurationException(
               String.format(
                  "@AliasFor declaration on %s points to itself. Specify 'annotation' to point to a same-named attribute on a meta-annotation.",
                  AttributeMethods.describe(attribute)
               )
            );
         }

         if (!this.isCompatibleReturnType(attribute.getReturnType(), target.getReturnType())) {
            throw new AnnotationConfigurationException(
               String.format(
                  "Misconfigured aliases: %s and %s must declare the same return type.",
                  AttributeMethods.describe(attribute),
                  AttributeMethods.describe(target)
               )
            );
         }

         if (this.isAliasPair(target) && checkAliasPair) {
            AliasFor targetAliasFor = target.getAnnotation(AliasFor.class);
            if (targetAliasFor != null) {
               Method mirror = this.resolveAliasTarget(target, targetAliasFor, false);
               if (!mirror.equals(attribute)) {
                  throw new AnnotationConfigurationException(
                     String.format(
                        "%s must be declared as an @AliasFor %s, not %s.",
                        StringUtils.capitalize(AttributeMethods.describe(target)),
                        AttributeMethods.describe(attribute),
                        AttributeMethods.describe(mirror)
                     )
                  );
               }
            }
         }

         return target;
      }
   }

   private boolean isAliasPair(Method target) {
      return this.annotationType == target.getDeclaringClass();
   }

   private boolean isCompatibleReturnType(Class<?> attributeType, Class<?> targetType) {
      return attributeType == targetType || attributeType == targetType.componentType();
   }

   private void processAliases() {
      List<Method> aliases = new ArrayList<>();

      for (int i = 0; i < this.attributes.size(); i++) {
         aliases.clear();
         aliases.add(this.attributes.get(i));
         this.collectAliases(aliases);
         if (aliases.size() > 1) {
            this.processAliases(i, aliases);
         }
      }
   }

   private void collectAliases(List<Method> aliases) {
      for (AnnotationTypeMapping mapping = this; mapping != null; mapping = mapping.source) {
         int size = aliases.size();

         for (int j = 0; j < size; j++) {
            List<Method> additional = mapping.aliasedBy.get(aliases.get(j));
            if (additional != null) {
               aliases.addAll(additional);
            }
         }
      }
   }

   private void processAliases(int attributeIndex, List<Method> aliases) {
      int rootAttributeIndex = this.getFirstRootAttributeIndex(aliases);

      for (AnnotationTypeMapping mapping = this; mapping != null; mapping = mapping.source) {
         if (rootAttributeIndex != -1 && mapping != this.root) {
            for (int i = 0; i < mapping.attributes.size(); i++) {
               if (aliases.contains(mapping.attributes.get(i))) {
                  mapping.aliasMappings[i] = rootAttributeIndex;
               }
            }
         }

         mapping.mirrorSets.updateFrom(aliases);
         mapping.claimedAliases.addAll(aliases);
         if (mapping.annotation != null) {
            int[] resolvedMirrors = mapping.mirrorSets.resolve(null, mapping.annotation, AnnotationUtils::invokeAnnotationMethod);

            for (int i = 0; i < mapping.attributes.size(); i++) {
               if (aliases.contains(mapping.attributes.get(i))) {
                  this.annotationValueMappings[attributeIndex] = resolvedMirrors[i];
                  this.annotationValueSource[attributeIndex] = mapping;
               }
            }
         }
      }
   }

   private int getFirstRootAttributeIndex(Collection<Method> aliases) {
      AttributeMethods rootAttributes = this.root.getAttributes();

      for (int i = 0; i < rootAttributes.size(); i++) {
         if (aliases.contains(rootAttributes.get(i))) {
            return i;
         }
      }

      return -1;
   }

   private boolean computeSynthesizableFlag(Set<Class<? extends Annotation>> visitedAnnotationTypes) {
      visitedAnnotationTypes.add(this.annotationType);

      for (int index : this.aliasMappings) {
         if (index != -1) {
            return true;
         }
      }

      if (!this.aliasedBy.isEmpty()) {
         return true;
      }

      for (int i = 0; i < this.annotationValueSource.length; i++) {
         if (this.annotationValueSource[i] != null && this.annotationValueSource[i] != this) {
            return true;
         }
      }

      if (this.getAttributes().hasNestedAnnotation()) {
         AttributeMethods attributeMethods = this.getAttributes();

         for (int i = 0; i < attributeMethods.size(); i++) {
            Method method = attributeMethods.get(i);
            Class<?> type = method.getReturnType();
            if (type.isAnnotation() || type.isArray() && type.componentType().isAnnotation()) {
               Class<? extends Annotation> annotationType = (Class<? extends Annotation>)(type.isAnnotation() ? type : type.componentType());
               if (visitedAnnotationTypes.add(annotationType)) {
                  AnnotationTypeMapping mapping = AnnotationTypeMappings.forAnnotationType(annotationType, visitedAnnotationTypes).get(0);
                  if (mapping.isSynthesizable()) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   void afterAllMappingsSet() {
      this.validateAllAliasesClaimed();

      for (int i = 0; i < this.mirrorSets.size(); i++) {
         this.validateMirrorSet(this.mirrorSets.get(i));
      }

      this.claimedAliases.clear();
   }

   private void validateAllAliasesClaimed() {
      for (int i = 0; i < this.attributes.size(); i++) {
         Method attribute = this.attributes.get(i);
         AliasFor aliasFor = AnnotationsScanner.getDeclaredAnnotation(attribute, AliasFor.class);
         if (aliasFor != null && !this.claimedAliases.contains(attribute)) {
            Method target = this.resolveAliasTarget(attribute, aliasFor);
            throw new AnnotationConfigurationException(
               String.format(
                  "@AliasFor declaration on %s declares an alias for %s which is not meta-present.",
                  AttributeMethods.describe(attribute),
                  AttributeMethods.describe(target)
               )
            );
         }
      }
   }

   private void validateMirrorSet(AnnotationTypeMapping.MirrorSets.MirrorSet mirrorSet) {
      Method firstAttribute = mirrorSet.get(0);
      Object firstDefaultValue = firstAttribute.getDefaultValue();

      for (int i = 1; i <= mirrorSet.size() - 1; i++) {
         Method mirrorAttribute = mirrorSet.get(i);
         Object mirrorDefaultValue = mirrorAttribute.getDefaultValue();
         if (firstDefaultValue == null || mirrorDefaultValue == null) {
            throw new AnnotationConfigurationException(
               String.format(
                  "Misconfigured aliases: %s and %s must declare default values.",
                  AttributeMethods.describe(firstAttribute),
                  AttributeMethods.describe(mirrorAttribute)
               )
            );
         }

         if (!ObjectUtils.nullSafeEquals(firstDefaultValue, mirrorDefaultValue)) {
            throw new AnnotationConfigurationException(
               String.format(
                  "Misconfigured aliases: %s and %s must declare the same default value.",
                  AttributeMethods.describe(firstAttribute),
                  AttributeMethods.describe(mirrorAttribute)
               )
            );
         }
      }
   }

   AnnotationTypeMapping getRoot() {
      return this.root;
   }

   @Nullable AnnotationTypeMapping getSource() {
      return this.source;
   }

   int getDistance() {
      return this.distance;
   }

   Class<? extends Annotation> getAnnotationType() {
      return this.annotationType;
   }

   List<Class<? extends Annotation>> getMetaTypes() {
      return this.metaTypes;
   }

   @Nullable Annotation getAnnotation() {
      return this.annotation;
   }

   AttributeMethods getAttributes() {
      return this.attributes;
   }

   int getAliasMapping(int attributeIndex) {
      return this.aliasMappings[attributeIndex];
   }

   @Nullable Object getMappedAnnotationValue(int attributeIndex, boolean metaAnnotationsOnly) {
      int mappedIndex = this.annotationValueMappings[attributeIndex];
      if (mappedIndex == -1) {
         return null;
      }

      AnnotationTypeMapping source = this.annotationValueSource[attributeIndex];
      return source == this && metaAnnotationsOnly ? null : AnnotationUtils.invokeAnnotationMethod(source.attributes.get(mappedIndex), source.annotation);
   }

   boolean isEquivalentToDefaultValue(int attributeIndex, Object value, ValueExtractor valueExtractor) {
      Method attribute = this.attributes.get(attributeIndex);
      return isEquivalentToDefaultValue(attribute, value, valueExtractor);
   }

   AnnotationTypeMapping.MirrorSets getMirrorSets() {
      return this.mirrorSets;
   }

   boolean isSynthesizable() {
      return this.synthesizable;
   }

   private static int[] filledIntArray(int size) {
      if (size == 0) {
         return EMPTY_INT_ARRAY;
      }

      int[] array = new int[size];
      Arrays.fill(array, -1);
      return array;
   }

   private static boolean isEquivalentToDefaultValue(Method attribute, Object value, ValueExtractor valueExtractor) {
      return areEquivalent(attribute.getDefaultValue(), value, valueExtractor);
   }

   private static boolean areEquivalent(@Nullable Object value, @Nullable Object extractedValue, ValueExtractor valueExtractor) {
      if (ObjectUtils.nullSafeEquals(value, extractedValue)) {
         return true;
      } else if (value instanceof Class<?> clazz && extractedValue instanceof String string) {
         return areEquivalent(clazz, string);
      } else if (value instanceof Class<?>[] classes && extractedValue instanceof String[] strings) {
         return areEquivalent(classes, strings);
      } else {
         return value instanceof Annotation annotation ? areEquivalent(annotation, extractedValue, valueExtractor) : false;
      }
   }

   private static boolean areEquivalent(Class<?>[] value, String[] extractedValue) {
      if (value.length != extractedValue.length) {
         return false;
      }

      for (int i = 0; i < value.length; i++) {
         if (!areEquivalent(value[i], extractedValue[i])) {
            return false;
         }
      }

      return true;
   }

   private static boolean areEquivalent(Class<?> value, String extractedValue) {
      return value.getName().equals(extractedValue);
   }

   private static boolean areEquivalent(Annotation annotation, @Nullable Object extractedValue, ValueExtractor valueExtractor) {
      AttributeMethods attributes = AttributeMethods.forAnnotationType(annotation.annotationType());

      for (int i = 0; i < attributes.size(); i++) {
         Method attribute = attributes.get(i);
         Object value1 = AnnotationUtils.invokeAnnotationMethod(attribute, annotation);
         Object value2;
         if (extractedValue instanceof TypeMappedAnnotation<?> typeMappedAnnotation) {
            value2 = typeMappedAnnotation.getValue(attribute.getName()).orElse(null);
         } else {
            value2 = valueExtractor.extract(attribute, extractedValue);
         }

         if (!areEquivalent(value1, value2, valueExtractor)) {
            return false;
         }
      }

      return true;
   }

   class MirrorSets {
      private AnnotationTypeMapping.MirrorSets.MirrorSet[] mirrorSets;
      private final AnnotationTypeMapping.MirrorSets.MirrorSet[] assigned = AnnotationTypeMapping.this.attributes.size() > 0
         ? new AnnotationTypeMapping.MirrorSets.MirrorSet[AnnotationTypeMapping.this.attributes.size()]
         : AnnotationTypeMapping.EMPTY_MIRROR_SETS;

      MirrorSets() {
         this.mirrorSets = AnnotationTypeMapping.EMPTY_MIRROR_SETS;
      }

      void updateFrom(Collection<Method> aliases) {
         AnnotationTypeMapping.MirrorSets.MirrorSet mirrorSet = null;
         int size = 0;
         int last = -1;

         for (int i = 0; i < AnnotationTypeMapping.this.attributes.size(); i++) {
            Method attribute = AnnotationTypeMapping.this.attributes.get(i);
            if (aliases.contains(attribute)) {
               if (++size > 1) {
                  if (mirrorSet == null) {
                     mirrorSet = new AnnotationTypeMapping.MirrorSets.MirrorSet();
                     this.assigned[last] = mirrorSet;
                  }

                  this.assigned[i] = mirrorSet;
               }

               last = i;
            }
         }

         if (mirrorSet != null) {
            mirrorSet.update();
            Set<AnnotationTypeMapping.MirrorSets.MirrorSet> unique = new LinkedHashSet<>(Arrays.asList(this.assigned));
            unique.remove(null);
            this.mirrorSets = unique.toArray(AnnotationTypeMapping.EMPTY_MIRROR_SETS);
         }
      }

      int size() {
         return this.mirrorSets.length;
      }

      AnnotationTypeMapping.MirrorSets.MirrorSet get(int index) {
         return this.mirrorSets[index];
      }

      AnnotationTypeMapping.MirrorSets.@Nullable MirrorSet getAssigned(int attributeIndex) {
         return this.assigned[attributeIndex];
      }

      int[] resolve(@Nullable Object source, @Nullable Object annotation, ValueExtractor valueExtractor) {
         if (AnnotationTypeMapping.this.attributes.size() == 0) {
            return AnnotationTypeMapping.EMPTY_INT_ARRAY;
         }

         int[] result = new int[AnnotationTypeMapping.this.attributes.size()];
         int i = 0;

         while (i < result.length) {
            result[i] = i++;
         }

         for (int ix = 0; ix < this.size(); ix++) {
            AnnotationTypeMapping.MirrorSets.MirrorSet mirrorSet = this.get(ix);
            int resolved = mirrorSet.resolve(source, (ValueExtractor)annotation, valueExtractor);

            for (int j = 0; j < mirrorSet.size; j++) {
               result[mirrorSet.indexes[j]] = resolved;
            }
         }

         return result;
      }

      class MirrorSet {
         private int size;
         private final int[] indexes = new int[AnnotationTypeMapping.this.attributes.size()];

         void update() {
            this.size = 0;
            Arrays.fill(this.indexes, -1);

            for (int i = 0; i < MirrorSets.this.assigned.length; i++) {
               if (MirrorSets.this.assigned[i] == this) {
                  this.indexes[this.size] = i;
                  this.size++;
               }
            }
         }

         <A> int resolve(@Nullable Object source, @Nullable A annotation, ValueExtractor valueExtractor) {
            int result = -1;
            Object lastValue = null;

            for (int i = 0; i < this.size; i++) {
               Method attribute = AnnotationTypeMapping.this.attributes.get(this.indexes[i]);
               Object value = valueExtractor.extract(attribute, annotation);
               boolean isDefaultValue = value == null || AnnotationTypeMapping.isEquivalentToDefaultValue(attribute, value, valueExtractor);
               if (!isDefaultValue && !ObjectUtils.nullSafeEquals(lastValue, value)) {
                  if (lastValue != null && !ObjectUtils.nullSafeEquals(lastValue, value)) {
                     String on = source != null ? " declared on " + source : "";
                     throw new AnnotationConfigurationException(
                        String.format(
                           "Different @AliasFor mirror values for annotation [%s]%s; attribute '%s' and its alias '%s' are declared with values of [%s] and [%s].",
                           ClassUtils.getCanonicalName(AnnotationTypeMapping.this.getAnnotationType()),
                           on,
                           AnnotationTypeMapping.this.attributes.get(result).getName(),
                           attribute.getName(),
                           ObjectUtils.nullSafeToString(lastValue),
                           ObjectUtils.nullSafeToString(value)
                        )
                     );
                  }

                  result = this.indexes[i];
                  lastValue = value;
               } else if (result == -1) {
                  result = this.indexes[i];
               }
            }

            return result;
         }

         int size() {
            return this.size;
         }

         Method get(int index) {
            int attributeIndex = this.indexes[index];
            return AnnotationTypeMapping.this.attributes.get(attributeIndex);
         }

         int getAttributeIndex(int index) {
            return this.indexes[index];
         }
      }
   }
}
