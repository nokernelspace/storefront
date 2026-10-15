package org.springframework.core.type.filter;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.util.ClassUtils;

public class AnnotationTypeFilter extends AbstractTypeHierarchyTraversingFilter {
   private final Class<? extends Annotation> annotationType;
   private final boolean considerMetaAnnotations;

   public AnnotationTypeFilter(Class<? extends Annotation> annotationType) {
      this(annotationType, true, false);
   }

   public AnnotationTypeFilter(Class<? extends Annotation> annotationType, boolean considerMetaAnnotations) {
      this(annotationType, considerMetaAnnotations, false);
   }

   public AnnotationTypeFilter(Class<? extends Annotation> annotationType, boolean considerMetaAnnotations, boolean considerInterfaces) {
      super(annotationType.isAnnotationPresent(Inherited.class), considerInterfaces);
      this.annotationType = annotationType;
      this.considerMetaAnnotations = considerMetaAnnotations;
   }

   public final Class<? extends Annotation> getAnnotationType() {
      return this.annotationType;
   }

   @Override
   protected boolean matchSelf(MetadataReader metadataReader) {
      AnnotationMetadata metadata = metadataReader.getAnnotationMetadata();
      return metadata.hasAnnotation(this.annotationType.getName()) || this.considerMetaAnnotations && metadata.hasMetaAnnotation(this.annotationType.getName());
   }

   @Override
   protected @Nullable Boolean matchSuperClass(String superClassName) {
      return this.hasAnnotation(superClassName);
   }

   @Override
   protected @Nullable Boolean matchInterface(String interfaceName) {
      return this.hasAnnotation(interfaceName);
   }

   protected @Nullable Boolean hasAnnotation(String typeName) {
      if (Object.class.getName().equals(typeName)) {
         return false;
      }

      if (!typeName.startsWith("java")) {
         return null;
      }

      if (!this.annotationType.getName().startsWith("java")) {
         return false;
      }

      try {
         Class<?> clazz = ClassUtils.forName(typeName, this.getClass().getClassLoader());
         return (this.considerMetaAnnotations ? AnnotationUtils.getAnnotation(clazz, this.annotationType) : clazz.getAnnotation(this.annotationType)) != null;
      } catch (Throwable var3) {
         return null;
      }
   }
}
