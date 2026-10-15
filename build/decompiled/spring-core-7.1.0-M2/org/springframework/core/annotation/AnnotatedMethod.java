package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

public class AnnotatedMethod {
   private static final Object NO_ANNOTATION = new Object();
   private final Method method;
   private final Method bridgedMethod;
   private final MethodParameter[] parameters;
   private final Map<Class<? extends Annotation>, Object> annotations;
   private volatile @Nullable List<Annotation[][]> inheritedParameterAnnotations;

   public AnnotatedMethod(Method method) {
      Assert.notNull(method, "Method is required");
      this.method = method;
      this.bridgedMethod = BridgeMethodResolver.findBridgedMethod(method);
      ReflectionUtils.makeAccessible(this.bridgedMethod);
      this.parameters = this.initMethodParameters();
      this.annotations = new ConcurrentHashMap<>(4);
   }

   protected AnnotatedMethod(AnnotatedMethod annotatedMethod) {
      Assert.notNull(annotatedMethod, "AnnotatedMethod is required");
      this.method = annotatedMethod.method;
      this.bridgedMethod = annotatedMethod.bridgedMethod;
      this.parameters = annotatedMethod.parameters;
      this.annotations = annotatedMethod.annotations;
      this.inheritedParameterAnnotations = annotatedMethod.inheritedParameterAnnotations;
   }

   public final Method getMethod() {
      return this.method;
   }

   protected final Method getBridgedMethod() {
      return this.bridgedMethod;
   }

   protected Class<?> getContainingClass() {
      return this.method.getDeclaringClass();
   }

   public final MethodParameter[] getMethodParameters() {
      return this.parameters;
   }

   private MethodParameter[] initMethodParameters() {
      int count = this.bridgedMethod.getParameterCount();
      MethodParameter[] result = new MethodParameter[count];

      for (int i = 0; i < count; i++) {
         result[i] = new AnnotatedMethod.AnnotatedMethodParameter(i);
      }

      return result;
   }

   public MethodParameter getReturnType() {
      return new AnnotatedMethod.AnnotatedMethodParameter(-1);
   }

   public MethodParameter getReturnValueType(@Nullable Object returnValue) {
      return new AnnotatedMethod.ReturnValueMethodParameter(returnValue);
   }

   public boolean isVoid() {
      return this.getReturnType().getParameterType() == void.class;
   }

   public <A extends Annotation> @Nullable A getMethodAnnotation(Class<A> annotationType) {
      Object result = this.annotations.computeIfAbsent(annotationType, key -> {
         Object value = AnnotatedElementUtils.findMergedAnnotation(this.method, annotationType);
         return value == null ? NO_ANNOTATION : value;
      });
      return (A)(result == NO_ANNOTATION ? null : result);
   }

   public <A extends Annotation> boolean hasMethodAnnotation(Class<A> annotationType) {
      return this.<A>getMethodAnnotation(annotationType) != null;
   }

   private List<Annotation[][]> getInheritedParameterAnnotations() {
      List<Annotation[][]> parameterAnnotations = this.inheritedParameterAnnotations;
      if (parameterAnnotations == null) {
         parameterAnnotations = new ArrayList<>();
         Class<?> clazz = this.method.getDeclaringClass();

         while (clazz != null) {
            for (Class<?> ifc : clazz.getInterfaces()) {
               for (Method candidate : ifc.getMethods()) {
                  if (this.isOverrideFor(candidate)) {
                     parameterAnnotations.add(candidate.getParameterAnnotations());
                  }
               }
            }

            clazz = clazz.getSuperclass();
            if (clazz == Object.class) {
               clazz = null;
            }

            if (clazz != null) {
               for (Method candidate : clazz.getDeclaredMethods()) {
                  if (this.isOverrideFor(candidate)) {
                     parameterAnnotations.add(candidate.getParameterAnnotations());
                  }
               }
            }
         }

         this.inheritedParameterAnnotations = parameterAnnotations;
      }

      return parameterAnnotations;
   }

   private boolean isOverrideFor(Method candidate) {
      if (!Modifier.isPrivate(candidate.getModifiers())
         && candidate.getName().equals(this.method.getName())
         && candidate.getParameterCount() == this.method.getParameterCount()) {
         Class<?>[] paramTypes = this.method.getParameterTypes();
         if (Arrays.equals(candidate.getParameterTypes(), paramTypes)) {
            return true;
         }

         for (int i = 0; i < paramTypes.length; i++) {
            if (paramTypes[i] != ResolvableType.forMethodParameter(candidate, i, this.method.getDeclaringClass()).toClass()) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof AnnotatedMethod otherHandlerMethod && this.method.equals(otherHandlerMethod.method);
   }

   @Override
   public int hashCode() {
      return this.method.hashCode();
   }

   @Override
   public String toString() {
      return this.method.toGenericString();
   }

   protected static @Nullable Object findProvidedArgument(MethodParameter parameter, @Nullable Object... providedArgs) {
      if (!ObjectUtils.isEmpty(providedArgs)) {
         for (Object providedArg : providedArgs) {
            if (parameter.getParameterType().isInstance(providedArg)) {
               return providedArg;
            }
         }
      }

      return null;
   }

   protected static String formatArgumentError(MethodParameter param, String message) {
      return "Could not resolve parameter ["
         + param.getParameterIndex()
         + "] in "
         + param.getExecutable().toGenericString()
         + (StringUtils.hasText(message) ? ": " + message : "");
   }

   protected class AnnotatedMethodParameter extends SynthesizingMethodParameter {
      private volatile Annotation @Nullable [] combinedAnnotations;

      public AnnotatedMethodParameter(int index) {
         super(AnnotatedMethod.this.getBridgedMethod(), index);
      }

      protected AnnotatedMethodParameter(AnnotatedMethod.AnnotatedMethodParameter original) {
         super(original);
         this.combinedAnnotations = original.combinedAnnotations;
      }

      @Override
      public Method getMethod() {
         return AnnotatedMethod.this.getBridgedMethod();
      }

      @Override
      public Class<?> getContainingClass() {
         return AnnotatedMethod.this.getContainingClass();
      }

      @Override
      public <T extends Annotation> @Nullable T getMethodAnnotation(Class<T> annotationType) {
         return AnnotatedMethod.this.getMethodAnnotation(annotationType);
      }

      @Override
      public <T extends Annotation> boolean hasMethodAnnotation(Class<T> annotationType) {
         return AnnotatedMethod.this.hasMethodAnnotation(annotationType);
      }

      @Override
      public Annotation[] getParameterAnnotations() {
         Annotation[] anns = this.combinedAnnotations;
         if (anns == null) {
            anns = super.getParameterAnnotations();
            int index = this.getParameterIndex();
            if (index >= 0) {
               for (Annotation[][] ifcAnns : AnnotatedMethod.this.getInheritedParameterAnnotations()) {
                  if (index < ifcAnns.length) {
                     Annotation[] paramAnns = ifcAnns[index];
                     if (paramAnns.length > 0) {
                        List<Annotation> merged = new ArrayList<>(anns.length + paramAnns.length);
                        merged.addAll(Arrays.asList(anns));

                        for (Annotation paramAnn : paramAnns) {
                           boolean existingType = false;

                           for (Annotation ann : anns) {
                              if (ann.annotationType() == paramAnn.annotationType()) {
                                 existingType = true;
                                 break;
                              }
                           }

                           if (!existingType) {
                              merged.add(this.adaptAnnotation(paramAnn));
                           }
                        }

                        anns = merged.toArray(new Annotation[0]);
                     }
                  }
               }
            }

            this.combinedAnnotations = anns;
         }

         return anns;
      }

      public AnnotatedMethod.AnnotatedMethodParameter clone() {
         return AnnotatedMethod.this.new AnnotatedMethodParameter(this);
      }
   }

   private class ReturnValueMethodParameter extends AnnotatedMethod.AnnotatedMethodParameter {
      private final @Nullable Class<?> returnValueType;

      public ReturnValueMethodParameter(@Nullable Object returnValue) {
         super(-1);
         this.returnValueType = returnValue != null ? returnValue.getClass() : null;
      }

      protected ReturnValueMethodParameter(AnnotatedMethod.ReturnValueMethodParameter original) {
         super(original);
         this.returnValueType = original.returnValueType;
      }

      @Override
      public Class<?> getParameterType() {
         return this.returnValueType != null ? this.returnValueType : super.getParameterType();
      }

      public AnnotatedMethod.ReturnValueMethodParameter clone() {
         return AnnotatedMethod.this.new ReturnValueMethodParameter(this);
      }
   }
}
