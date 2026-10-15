package org.springframework.core.type;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.RepeatableContainers;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;

public class StandardMethodMetadata implements MethodMetadata {
   private final Method introspectedMethod;
   private final boolean nestedAnnotationsAsMap;
   private final MergedAnnotations mergedAnnotations;

   @Deprecated(since = "5.2")
   public StandardMethodMetadata(Method introspectedMethod) {
      this(introspectedMethod, false);
   }

   StandardMethodMetadata(Method introspectedMethod, boolean nestedAnnotationsAsMap) {
      Assert.notNull(introspectedMethod, "Method must not be null");
      this.introspectedMethod = introspectedMethod;
      this.nestedAnnotationsAsMap = nestedAnnotationsAsMap;
      this.mergedAnnotations = MergedAnnotations.from(introspectedMethod, MergedAnnotations.SearchStrategy.DIRECT, RepeatableContainers.none());
   }

   @Override
   public MergedAnnotations getAnnotations() {
      return this.mergedAnnotations;
   }

   public final Method getIntrospectedMethod() {
      return this.introspectedMethod;
   }

   @Override
   public String getMethodName() {
      return this.introspectedMethod.getName();
   }

   @Override
   public String getDeclaringClassName() {
      return this.introspectedMethod.getDeclaringClass().getName();
   }

   @Override
   public String getReturnTypeName() {
      return this.introspectedMethod.getReturnType().getTypeName();
   }

   @Override
   public boolean isAbstract() {
      return Modifier.isAbstract(this.introspectedMethod.getModifiers());
   }

   @Override
   public boolean isStatic() {
      return Modifier.isStatic(this.introspectedMethod.getModifiers());
   }

   @Override
   public boolean isFinal() {
      return Modifier.isFinal(this.introspectedMethod.getModifiers());
   }

   @Override
   public boolean isOverridable() {
      return !this.isStatic() && !this.isFinal() && !this.isPrivate();
   }

   private boolean isPrivate() {
      return Modifier.isPrivate(this.introspectedMethod.getModifiers());
   }

   @Override
   public @Nullable Map<String, @Nullable Object> getAnnotationAttributes(String annotationName, boolean classValuesAsString) {
      return this.nestedAnnotationsAsMap
         ? MethodMetadata.super.getAnnotationAttributes(annotationName, classValuesAsString)
         : AnnotatedElementUtils.getMergedAnnotationAttributes(this.introspectedMethod, annotationName, classValuesAsString, false);
   }

   @Override
   public @Nullable MultiValueMap<String, @Nullable Object> getAllAnnotationAttributes(String annotationName, boolean classValuesAsString) {
      return this.nestedAnnotationsAsMap
         ? MethodMetadata.super.getAllAnnotationAttributes(annotationName, classValuesAsString)
         : AnnotatedElementUtils.getAllAnnotationAttributes(this.introspectedMethod, annotationName, classValuesAsString, false);
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof StandardMethodMetadata that && this.introspectedMethod.equals(that.introspectedMethod);
   }

   @Override
   public int hashCode() {
      return this.introspectedMethod.hashCode();
   }

   @Override
   public String toString() {
      return this.introspectedMethod.toString();
   }

   static MethodMetadata from(Method introspectedMethod) {
      return new StandardMethodMetadata(introspectedMethod, true);
   }
}
