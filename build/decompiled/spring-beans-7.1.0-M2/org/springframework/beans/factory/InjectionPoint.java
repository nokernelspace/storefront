package org.springframework.beans.factory;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

public class InjectionPoint {
   protected @Nullable MethodParameter methodParameter;
   protected @Nullable Field field;
   private volatile Annotation @Nullable [] fieldAnnotations;

   public InjectionPoint(MethodParameter methodParameter) {
      Assert.notNull(methodParameter, "MethodParameter must not be null");
      this.methodParameter = methodParameter;
   }

   public InjectionPoint(Field field) {
      Assert.notNull(field, "Field must not be null");
      this.field = field;
   }

   protected InjectionPoint(InjectionPoint original) {
      this.methodParameter = original.methodParameter != null ? new MethodParameter(original.methodParameter) : null;
      this.field = original.field;
      this.fieldAnnotations = original.fieldAnnotations;
   }

   protected InjectionPoint() {
   }

   public @Nullable MethodParameter getMethodParameter() {
      return this.methodParameter;
   }

   public @Nullable Field getField() {
      return this.field;
   }

   protected final MethodParameter obtainMethodParameter() {
      Assert.state(this.methodParameter != null, "MethodParameter is not available");
      return this.methodParameter;
   }

   public Annotation[] getAnnotations() {
      if (this.field != null) {
         Annotation[] fieldAnnotations = this.fieldAnnotations;
         if (fieldAnnotations == null) {
            fieldAnnotations = this.field.getAnnotations();
            this.fieldAnnotations = fieldAnnotations;
         }

         return fieldAnnotations;
      } else {
         return this.obtainMethodParameter().getParameterAnnotations();
      }
   }

   public <A extends Annotation> @Nullable A getAnnotation(Class<A> annotationType) {
      return (A)(this.field != null ? this.field.getAnnotation(annotationType) : this.obtainMethodParameter().getParameterAnnotation(annotationType));
   }

   public Class<?> getDeclaredType() {
      return this.field != null ? this.field.getType() : this.obtainMethodParameter().getParameterType();
   }

   public Member getMember() {
      return this.field != null ? this.field : this.obtainMethodParameter().getMember();
   }

   public AnnotatedElement getAnnotatedElement() {
      return this.field != null ? this.field : this.obtainMethodParameter().getAnnotatedElement();
   }

   @Override
   public boolean equals(@Nullable Object other) {
      if (this == other) {
         return true;
      } else if (other != null && this.getClass() == other.getClass()) {
         InjectionPoint otherPoint = (InjectionPoint)other;
         return ObjectUtils.nullSafeEquals(this.field, otherPoint.field) && ObjectUtils.nullSafeEquals(this.methodParameter, otherPoint.methodParameter);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.field, this.methodParameter);
   }

   @Override
   public String toString() {
      return this.field != null ? "field '" + this.field.getName() + "'" : String.valueOf(this.methodParameter);
   }
}
