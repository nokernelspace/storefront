package org.springframework.core.type;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.RepeatableContainers;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ReflectionUtils;

public class StandardAnnotationMetadata extends StandardClassMetadata implements AnnotationMetadata {
   private final MergedAnnotations mergedAnnotations;
   private final boolean nestedAnnotationsAsMap;
   private @Nullable Set<String> annotationTypes;

   @Deprecated(since = "5.2")
   public StandardAnnotationMetadata(Class<?> introspectedClass) {
      this(introspectedClass, false);
   }

   @Deprecated(since = "5.2")
   public StandardAnnotationMetadata(Class<?> introspectedClass, boolean nestedAnnotationsAsMap) {
      super(introspectedClass);
      this.mergedAnnotations = MergedAnnotations.from(introspectedClass, MergedAnnotations.SearchStrategy.INHERITED_ANNOTATIONS, RepeatableContainers.none());
      this.nestedAnnotationsAsMap = nestedAnnotationsAsMap;
   }

   @Override
   public MergedAnnotations getAnnotations() {
      return this.mergedAnnotations;
   }

   @Override
   public Set<String> getAnnotationTypes() {
      Set<String> annotationTypes = this.annotationTypes;
      if (annotationTypes == null) {
         annotationTypes = Collections.unmodifiableSet(AnnotationMetadata.super.getAnnotationTypes());
         this.annotationTypes = annotationTypes;
      }

      return annotationTypes;
   }

   @Override
   public @Nullable Map<String, @Nullable Object> getAnnotationAttributes(String annotationName, boolean classValuesAsString) {
      return this.nestedAnnotationsAsMap
         ? AnnotationMetadata.super.getAnnotationAttributes(annotationName, classValuesAsString)
         : AnnotatedElementUtils.getMergedAnnotationAttributes(this.getIntrospectedClass(), annotationName, classValuesAsString, false);
   }

   @Override
   public @Nullable MultiValueMap<String, @Nullable Object> getAllAnnotationAttributes(String annotationName, boolean classValuesAsString) {
      return this.nestedAnnotationsAsMap
         ? AnnotationMetadata.super.getAllAnnotationAttributes(annotationName, classValuesAsString)
         : AnnotatedElementUtils.getAllAnnotationAttributes(this.getIntrospectedClass(), annotationName, classValuesAsString, false);
   }

   @Override
   public boolean hasAnnotatedMethods(String annotationName) {
      if (AnnotationUtils.isCandidateClass(this.getIntrospectedClass(), annotationName)) {
         try {
            Method[] methods = ReflectionUtils.getDeclaredMethods(this.getIntrospectedClass());

            for (Method method : methods) {
               if (isAnnotatedMethod(method, annotationName)) {
                  return true;
               }
            }
         } catch (Throwable ex) {
            throw new IllegalStateException("Failed to introspect annotated methods on " + this.getIntrospectedClass(), ex);
         }
      }

      return false;
   }

   @Override
   public Set<MethodMetadata> getAnnotatedMethods(String annotationName) {
      Set<MethodMetadata> result = new LinkedHashSet<>(4);
      if (AnnotationUtils.isCandidateClass(this.getIntrospectedClass(), annotationName)) {
         ReflectionUtils.doWithLocalMethods(this.getIntrospectedClass(), method -> {
            if (isAnnotatedMethod(method, annotationName)) {
               result.add(new StandardMethodMetadata(method, this.nestedAnnotationsAsMap));
            }
         });
      }

      return result;
   }

   @Override
   public Set<MethodMetadata> getDeclaredMethods() {
      Set<MethodMetadata> result = new LinkedHashSet<>(16);
      ReflectionUtils.doWithLocalMethods(this.getIntrospectedClass(), method -> result.add(new StandardMethodMetadata(method, this.nestedAnnotationsAsMap)));
      return result;
   }

   private static boolean isAnnotatedMethod(Method method, String annotationName) {
      return !method.isBridge() && method.getAnnotations().length > 0 && AnnotatedElementUtils.isAnnotated(method, annotationName);
   }

   static AnnotationMetadata from(Class<?> introspectedClass) {
      return new StandardAnnotationMetadata(introspectedClass, true);
   }
}
