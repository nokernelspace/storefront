package org.springframework.beans.factory.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.SynthesizingMethodParameter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

public final class ParameterResolutionDelegate {
   private static final Annotation[] EMPTY_ANNOTATION_ARRAY = new Annotation[0];
   private static final AnnotatedElement EMPTY_ANNOTATED_ELEMENT = new AnnotatedElement() {
      @Override
      public <T extends Annotation> @Nullable T getAnnotation(Class<T> annotationClass) {
         return null;
      }

      @Override
      public Annotation[] getAnnotations() {
         return ParameterResolutionDelegate.EMPTY_ANNOTATION_ARRAY;
      }

      @Override
      public Annotation[] getDeclaredAnnotations() {
         return ParameterResolutionDelegate.EMPTY_ANNOTATION_ARRAY;
      }
   };

   private ParameterResolutionDelegate() {
   }

   public static boolean isAutowirable(Parameter parameter, int parameterIndex) {
      Assert.notNull(parameter, "Parameter must not be null");
      AnnotatedElement annotatedParameter = getEffectiveAnnotatedParameter(parameter, parameterIndex);
      return AnnotatedElementUtils.hasAnnotation(annotatedParameter, Autowired.class)
         || AnnotatedElementUtils.hasAnnotation(annotatedParameter, Qualifier.class)
         || AnnotatedElementUtils.hasAnnotation(annotatedParameter, Value.class);
   }

   public static @Nullable Object resolveDependency(Parameter parameter, int parameterIndex, Class<?> containingClass, AutowireCapableBeanFactory beanFactory) throws BeansException {
      return resolveDependency(parameter, parameterIndex, null, containingClass, beanFactory);
   }

   public static @Nullable Object resolveDependency(
      Parameter parameter, int parameterIndex, @Nullable String parameterName, Class<?> containingClass, AutowireCapableBeanFactory beanFactory
   ) throws BeansException {
      Assert.notNull(parameter, "Parameter must not be null");
      Assert.notNull(containingClass, "Containing class must not be null");
      Assert.notNull(beanFactory, "AutowireCapableBeanFactory must not be null");
      AnnotatedElement annotatedParameter = getEffectiveAnnotatedParameter(parameter, parameterIndex);
      Autowired autowired = (Autowired)AnnotatedElementUtils.findMergedAnnotation(annotatedParameter, Autowired.class);
      boolean required = autowired == null || autowired.required();
      MethodParameter methodParameter = SynthesizingMethodParameter.forExecutable(parameter.getDeclaringExecutable(), parameterIndex);
      DependencyDescriptor descriptor = new ParameterResolutionDelegate.NamedParameterDependencyDescriptor(methodParameter, required, parameterName);
      descriptor.setContainingClass(containingClass);
      return beanFactory.resolveDependency(descriptor, null);
   }

   private static AnnotatedElement getEffectiveAnnotatedParameter(Parameter parameter, int index) {
      Executable executable = parameter.getDeclaringExecutable();
      if (executable instanceof Constructor
         && ClassUtils.isInnerClass(executable.getDeclaringClass())
         && executable.getParameterAnnotations().length == executable.getParameterCount() - 1) {
         return index == 0 ? EMPTY_ANNOTATED_ELEMENT : executable.getParameters()[index - 1];
      } else {
         return parameter;
      }
   }

   private static class NamedParameterDependencyDescriptor extends DependencyDescriptor {
      private final @Nullable String parameterName;

      NamedParameterDependencyDescriptor(MethodParameter methodParameter, boolean required, @Nullable String parameterName) {
         super(methodParameter, required);
         this.parameterName = parameterName;
      }

      @Override
      public @Nullable String getDependencyName() {
         return this.parameterName != null ? this.parameterName : super.getDependencyName();
      }

      @Override
      public boolean usesStandardBeanLookup() {
         return true;
      }
   }
}
