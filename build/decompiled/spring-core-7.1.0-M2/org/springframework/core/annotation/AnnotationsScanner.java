package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

abstract class AnnotationsScanner {
   private static final @Nullable Annotation[] NO_ANNOTATIONS = new Annotation[0];
   private static final Method[] NO_METHODS = new Method[0];
   private static final Map<AnnotatedElement, Annotation[]> declaredAnnotationCache = new ConcurrentReferenceHashMap<>(256);
   private static final Map<Class<?>, @Nullable Method[]> baseTypeMethodsCache = new ConcurrentReferenceHashMap<>(256);

   private AnnotationsScanner() {
   }

   static <C, R> @Nullable R scan(
      C context,
      AnnotatedElement source,
      MergedAnnotations.SearchStrategy searchStrategy,
      Predicate<Class<?>> searchEnclosingClass,
      AnnotationsProcessor<C, R> processor
   ) {
      R result = process(context, source, searchStrategy, searchEnclosingClass, processor);
      return processor.finish(result);
   }

   private static <C, R> @Nullable R process(
      C context,
      AnnotatedElement source,
      MergedAnnotations.SearchStrategy searchStrategy,
      Predicate<Class<?>> searchEnclosingClass,
      AnnotationsProcessor<C, R> processor
   ) {
      if (source instanceof Class<?> clazz) {
         return processClass(context, clazz, searchStrategy, searchEnclosingClass, processor);
      } else {
         return source instanceof Method method ? processMethod(context, method, searchStrategy, processor) : processElement(context, source, processor);
      }
   }

   private static <C, R> @Nullable R processClass(
      C context,
      Class<?> source,
      MergedAnnotations.SearchStrategy searchStrategy,
      Predicate<Class<?>> searchEnclosingClass,
      AnnotationsProcessor<C, R> processor
   ) {
      return (R)(switch (searchStrategy) {
         case DIRECT -> processElement(context, source, processor);
         case INHERITED_ANNOTATIONS -> processClassInheritedAnnotations(context, source, processor);
         case SUPERCLASS -> processClassHierarchy(context, source, processor, false, MergedAnnotations.Search.never);
         case TYPE_HIERARCHY -> processClassHierarchy(context, source, processor, true, searchEnclosingClass);
      });
   }

   private static <C, R> R processClassInheritedAnnotations(C context, Class<?> source, AnnotationsProcessor<C, R> processor) {
      try {
         if (isWithoutHierarchy(source, MergedAnnotations.Search.never)) {
            return processElement(context, source, processor);
         }

         Annotation[] relevant = null;
         int remaining = Integer.MAX_VALUE;
         int aggregateIndex = 0;
         Class<?> root = source;

         while (source != null && source != Object.class && remaining > 0 && !hasPlainJavaAnnotationsOnly(source)) {
            R result = processor.doWithAggregate(context, aggregateIndex);
            if (result != null) {
               return result;
            }

            Annotation[] declaredAnns = getDeclaredAnnotations(source, true);
            if (declaredAnns.length > 0) {
               if (relevant == null) {
                  relevant = root.getAnnotations();
                  remaining = relevant.length;
               }

               for (int i = 0; i < declaredAnns.length; i++) {
                  if (declaredAnns[i] != null) {
                     boolean isRelevant = false;

                     for (int relevantIndex = 0; relevantIndex < relevant.length; relevantIndex++) {
                        if (relevant[relevantIndex] != null && declaredAnns[i].annotationType() == relevant[relevantIndex].annotationType()) {
                           isRelevant = true;
                           relevant[relevantIndex] = null;
                           remaining--;
                           break;
                        }
                     }

                     if (!isRelevant) {
                        declaredAnns[i] = null;
                     }
                  }
               }
            }

            result = processor.doWithAnnotations(context, aggregateIndex, source, declaredAnns);
            if (result != null) {
               return result;
            }

            source = source.getSuperclass();
            aggregateIndex++;
         }
      } catch (Throwable ex) {
         AnnotationUtils.handleIntrospectionFailure(source, ex);
      }

      return null;
   }

   private static <C, R> @Nullable R processClassHierarchy(
      C context, Class<?> source, AnnotationsProcessor<C, R> processor, boolean includeInterfaces, Predicate<Class<?>> searchEnclosingClass
   ) {
      return processClassHierarchy(context, new int[]{0}, source, processor, includeInterfaces, searchEnclosingClass);
   }

   private static <C, R> R processClassHierarchy(
      C context,
      int[] aggregateIndex,
      Class<?> source,
      AnnotationsProcessor<C, R> processor,
      boolean includeInterfaces,
      Predicate<Class<?>> searchEnclosingClass
   ) {
      try {
         R result = processor.doWithAggregate(context, aggregateIndex[0]);
         if (result != null) {
            return result;
         }

         if (hasPlainJavaAnnotationsOnly(source)) {
            return null;
         }

         Annotation[] annotations = getDeclaredAnnotations(source, false);
         result = processor.doWithAnnotations(context, aggregateIndex[0], source, annotations);
         if (result != null) {
            return result;
         }

         aggregateIndex[0]++;
         if (includeInterfaces) {
            for (Class<?> interfaceType : source.getInterfaces()) {
               R interfacesResult = processClassHierarchy(context, aggregateIndex, interfaceType, processor, true, searchEnclosingClass);
               if (interfacesResult != null) {
                  return interfacesResult;
               }
            }
         }

         Class<?> superclass = source.getSuperclass();
         if (superclass != Object.class && superclass != null) {
            R superclassResult = processClassHierarchy(context, aggregateIndex, superclass, processor, includeInterfaces, searchEnclosingClass);
            if (superclassResult != null) {
               return superclassResult;
            }
         }

         if (searchEnclosingClass.test(source)) {
            try {
               Class<?> enclosingClass = source.getEnclosingClass();
               if (enclosingClass != null) {
                  R enclosingResult = processClassHierarchy(context, aggregateIndex, enclosingClass, processor, includeInterfaces, searchEnclosingClass);
                  if (enclosingResult != null) {
                     return enclosingResult;
                  }
               }
            } catch (Throwable ex) {
               AnnotationUtils.handleIntrospectionFailure(source, ex);
            }
         }
      } catch (Throwable ex) {
         AnnotationUtils.handleIntrospectionFailure(source, ex);
      }

      return null;
   }

   private static <C, R> @Nullable R processMethod(
      C context, Method source, MergedAnnotations.SearchStrategy searchStrategy, AnnotationsProcessor<C, R> processor
   ) {
      return (R)(switch (searchStrategy) {
         case DIRECT, INHERITED_ANNOTATIONS -> processMethodInheritedAnnotations(context, source, processor);
         case SUPERCLASS -> processMethodHierarchy(context, new int[]{0}, source.getDeclaringClass(), processor, source, false);
         case TYPE_HIERARCHY -> processMethodHierarchy(context, new int[]{0}, source.getDeclaringClass(), processor, source, true);
      });
   }

   private static <C, R> @Nullable R processMethodInheritedAnnotations(C context, Method source, AnnotationsProcessor<C, R> processor) {
      try {
         R result = processor.doWithAggregate(context, 0);
         return result != null ? result : processMethodAnnotations(context, 0, source, processor);
      } catch (Throwable ex) {
         AnnotationUtils.handleIntrospectionFailure(source, ex);
         return null;
      }
   }

   private static <C, R> @Nullable R processMethodHierarchy(
      C context, int[] aggregateIndex, Class<?> sourceClass, AnnotationsProcessor<C, R> processor, Method rootMethod, boolean includeInterfaces
   ) {
      try {
         R result = processor.doWithAggregate(context, aggregateIndex[0]);
         if (result != null) {
            return result;
         }

         if (hasPlainJavaAnnotationsOnly(sourceClass)) {
            return null;
         }

         boolean calledProcessor = false;
         if (sourceClass == rootMethod.getDeclaringClass()) {
            result = processMethodAnnotations(context, aggregateIndex[0], rootMethod, processor);
            calledProcessor = true;
            if (result != null) {
               return result;
            }
         } else {
            for (Method candidateMethod : getBaseTypeMethods(context, sourceClass)) {
               if (candidateMethod != null && isOverride(rootMethod, candidateMethod)) {
                  result = processMethodAnnotations(context, aggregateIndex[0], candidateMethod, processor);
                  calledProcessor = true;
                  if (result != null) {
                     return result;
                  }
               }
            }
         }

         if (Modifier.isPrivate(rootMethod.getModifiers())) {
            return null;
         }

         if (calledProcessor) {
            aggregateIndex[0]++;
         }

         if (includeInterfaces) {
            for (Class<?> interfaceType : sourceClass.getInterfaces()) {
               R interfacesResult = processMethodHierarchy(context, aggregateIndex, interfaceType, processor, rootMethod, true);
               if (interfacesResult != null) {
                  return interfacesResult;
               }
            }
         }

         Class<?> superclass = sourceClass.getSuperclass();
         if (superclass != Object.class && superclass != null) {
            R superclassResult = processMethodHierarchy(context, aggregateIndex, superclass, processor, rootMethod, includeInterfaces);
            if (superclassResult != null) {
               return superclassResult;
            }
         }
      } catch (Throwable ex) {
         AnnotationUtils.handleIntrospectionFailure(rootMethod, ex);
      }

      return null;
   }

   private static <C> Method[] getBaseTypeMethods(C context, Class<?> baseType) {
      if (baseType != Object.class && !hasPlainJavaAnnotationsOnly(baseType)) {
         Method[] methods = baseTypeMethodsCache.get(baseType);
         if (methods == null) {
            methods = ReflectionUtils.getDeclaredMethods(baseType);
            int cleared = 0;

            for (int i = 0; i < methods.length; i++) {
               if (Modifier.isPrivate(methods[i].getModifiers())
                  || hasPlainJavaAnnotationsOnly(methods[i])
                  || getDeclaredAnnotations(methods[i], false).length == 0) {
                  methods[i] = null;
                  cleared++;
               }
            }

            if (cleared == methods.length) {
               methods = NO_METHODS;
            }

            baseTypeMethodsCache.put(baseType, methods);
         }

         return methods;
      } else {
         return NO_METHODS;
      }
   }

   private static boolean isOverride(Method rootMethod, Method candidateMethod) {
      return !Modifier.isPrivate(candidateMethod.getModifiers())
         && candidateMethod.getParameterCount() == rootMethod.getParameterCount()
         && candidateMethod.getName().equals(rootMethod.getName())
         && hasSameParameterTypes(rootMethod, candidateMethod);
   }

   private static boolean hasSameParameterTypes(Method rootMethod, Method candidateMethod) {
      Class<?>[] rootParameterTypes = rootMethod.getParameterTypes();
      Class<?>[] candidateParameterTypes = candidateMethod.getParameterTypes();
      return Arrays.equals(candidateParameterTypes, rootParameterTypes) ? true : hasSameGenericTypeParameters(rootMethod, candidateMethod, rootParameterTypes);
   }

   private static boolean hasSameGenericTypeParameters(Method rootMethod, Method candidateMethod, Class<?>[] rootParameterTypes) {
      Class<?> rootDeclaringClass = rootMethod.getDeclaringClass();
      Class<?> candidateDeclaringClass = candidateMethod.getDeclaringClass();
      if (!candidateDeclaringClass.isAssignableFrom(rootDeclaringClass)) {
         return false;
      }

      for (int i = 0; i < rootParameterTypes.length; i++) {
         Class<?> resolvedParameterType = ResolvableType.forMethodParameter(candidateMethod, i, rootDeclaringClass).toClass();
         if (rootParameterTypes[i] != resolvedParameterType) {
            return false;
         }
      }

      return true;
   }

   private static <C, R> R processMethodAnnotations(C context, int aggregateIndex, Method source, AnnotationsProcessor<C, R> processor) {
      Annotation[] annotations = getDeclaredAnnotations(source, false);
      R result = processor.doWithAnnotations(context, aggregateIndex, source, annotations);
      if (result != null) {
         return result;
      }

      Method bridgedMethod = BridgeMethodResolver.findBridgedMethod(source);
      if (bridgedMethod != source) {
         Annotation[] bridgedAnnotations = getDeclaredAnnotations(bridgedMethod, true);

         for (int i = 0; i < bridgedAnnotations.length; i++) {
            if (ObjectUtils.containsElement(annotations, bridgedAnnotations[i])) {
               bridgedAnnotations[i] = null;
            }
         }

         return processor.doWithAnnotations(context, aggregateIndex, source, bridgedAnnotations);
      } else {
         return null;
      }
   }

   private static <C, R> @Nullable R processElement(C context, AnnotatedElement source, AnnotationsProcessor<C, R> processor) {
      try {
         R result = processor.doWithAggregate(context, 0);
         return result != null ? result : processor.doWithAnnotations(context, 0, source, getDeclaredAnnotations(source, false));
      } catch (Throwable ex) {
         AnnotationUtils.handleIntrospectionFailure(source, ex);
         return null;
      }
   }

   static <A extends Annotation> A getDeclaredAnnotation(AnnotatedElement source, Class<A> annotationType) {
      Annotation[] annotations = getDeclaredAnnotations(source, false);

      for (Annotation annotation : annotations) {
         if (annotation != null && annotationType == annotation.annotationType()) {
            return (A)annotation;
         }
      }

      return null;
   }

   static Annotation[] getDeclaredAnnotations(AnnotatedElement source, boolean defensive) {
      boolean cached = false;
      Annotation[] annotations = declaredAnnotationCache.get(source);
      if (annotations != null) {
         cached = true;
      } else {
         annotations = source.getDeclaredAnnotations();
         if (annotations.length != 0) {
            boolean allIgnored = true;

            for (int i = 0; i < annotations.length; i++) {
               Annotation annotation = annotations[i];
               if (!isIgnorable(annotation.annotationType()) && AttributeMethods.forAnnotationType(annotation.annotationType()).canLoad(annotation, source)) {
                  allIgnored = false;
               } else {
                  annotations[i] = null;
               }
            }

            annotations = allIgnored ? NO_ANNOTATIONS : annotations;
            if (source instanceof Class || source instanceof Member) {
               declaredAnnotationCache.put(source, annotations);
               cached = true;
            }
         }
      }

      return defensive && annotations.length != 0 && cached ? (Annotation[])annotations.clone() : annotations;
   }

   private static boolean isIgnorable(Class<?> annotationType) {
      return AnnotationFilter.PLAIN.matches(annotationType);
   }

   static boolean isKnownEmpty(AnnotatedElement source, MergedAnnotations.SearchStrategy searchStrategy, Predicate<Class<?>> searchEnclosingClass) {
      if (hasPlainJavaAnnotationsOnly(source)) {
         return true;
      } else if (searchStrategy != MergedAnnotations.SearchStrategy.DIRECT && !isWithoutHierarchy(source, searchEnclosingClass)) {
         return false;
      } else {
         return source instanceof Method method && method.isBridge() ? false : getDeclaredAnnotations(source, false).length == 0;
      }
   }

   static boolean hasPlainJavaAnnotationsOnly(@Nullable Object annotatedElement) {
      if (annotatedElement instanceof Class<?> clazz) {
         return hasPlainJavaAnnotationsOnly(clazz);
      } else {
         return annotatedElement instanceof Member member ? hasPlainJavaAnnotationsOnly(member.getDeclaringClass()) : false;
      }
   }

   static boolean hasPlainJavaAnnotationsOnly(Class<?> type) {
      return type.getName().startsWith("java.") || type == Ordered.class;
   }

   private static boolean isWithoutHierarchy(AnnotatedElement source, Predicate<Class<?>> searchEnclosingClass) {
      if (source == Object.class) {
         return true;
      } else if (source instanceof Class<?> sourceClass) {
         boolean noSuperTypes = sourceClass.getSuperclass() == Object.class && sourceClass.getInterfaces().length == 0;
         return searchEnclosingClass.test(sourceClass) ? noSuperTypes && sourceClass.getEnclosingClass() == null : noSuperTypes;
      } else {
         return !(source instanceof Method sourceMethod)
            ? true
            : Modifier.isPrivate(sourceMethod.getModifiers()) || isWithoutHierarchy(sourceMethod.getDeclaringClass(), searchEnclosingClass);
      }
   }

   static void clearCache() {
      declaredAnnotationCache.clear();
      baseTypeMethodsCache.clear();
   }
}
