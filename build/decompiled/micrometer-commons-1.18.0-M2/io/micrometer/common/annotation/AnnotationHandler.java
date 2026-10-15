package io.micrometer.common.annotation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.jspecify.annotations.Nullable;

public class AnnotationHandler<T> {
   private static final InternalLogger log = InternalLoggerFactory.getInstance(AnnotationHandler.class);
   private final BiConsumer<KeyValue, T> keyValueConsumer;
   private final Function<Class<? extends ValueResolver>, ? extends ValueResolver> resolverProvider;
   private final Function<Class<? extends ValueExpressionResolver>, ? extends ValueExpressionResolver> expressionResolverProvider;
   private final Class<? extends Annotation> annotationClass;
   private final BiFunction<Annotation, Object, KeyValue> toKeyValue;

   public AnnotationHandler(
      BiConsumer<KeyValue, T> keyValueConsumer,
      Function<Class<? extends ValueResolver>, ? extends ValueResolver> resolverProvider,
      Function<Class<? extends ValueExpressionResolver>, ? extends ValueExpressionResolver> expressionResolverProvider,
      Class<? extends Annotation> annotation,
      BiFunction<Annotation, Object, KeyValue> toKeyValue
   ) {
      this.keyValueConsumer = keyValueConsumer;
      this.resolverProvider = resolverProvider;
      this.expressionResolverProvider = expressionResolverProvider;
      this.annotationClass = annotation;
      this.toKeyValue = toKeyValue;
   }

   public void addAnnotatedParameters(T objectToModify, ProceedingJoinPoint pjp) {
      try {
         Method method = ((MethodSignature)pjp.getSignature()).getMethod();
         method = tryToTakeMethodFromTargetClass(pjp, method);
         List<AnnotatedObject> annotatedParameters = AnnotationUtils.findAnnotatedParameters(this.annotationClass, method, pjp.getArgs());
         this.addAnnotatedParametersFromInterfaces(pjp, method, annotatedParameters);
         this.addAnnotatedObjects(objectToModify, annotatedParameters);
      } catch (Exception ex) {
         log.error("Exception occurred while trying to add annotated parameters", ex);
      }
   }

   public void addAnnotatedMethodResult(T objectToModify, ProceedingJoinPoint pjp, @Nullable Object result) {
      try {
         Method method = ((MethodSignature)pjp.getSignature()).getMethod();
         method = tryToTakeMethodFromTargetClass(pjp, method);
         List<AnnotatedObject> annotatedResult = new ArrayList<>();
         Arrays.stream(method.getAnnotationsByType(this.annotationClass))
            .map(annotation -> new AnnotatedObject(annotation, result))
            .forEach(annotatedResult::add);
         this.getMethodAnnotationsFromInterfaces(pjp, method).stream().map(annotation -> new AnnotatedObject(annotation, result)).forEach(annotatedResult::add);
         this.addAnnotatedObjects(objectToModify, annotatedResult);
      } catch (Exception ex) {
         log.error("Exception occurred while trying to add annotated method result", ex);
      }
   }

   private static Method tryToTakeMethodFromTargetClass(ProceedingJoinPoint pjp, Method method) {
      try {
         return pjp.getTarget().getClass().getDeclaredMethod(method.getName(), method.getParameterTypes());
      } catch (NoSuchMethodException var3) {
         return method;
      }
   }

   private void addAnnotatedParametersFromInterfaces(ProceedingJoinPoint pjp, Method mostSpecificMethod, List<AnnotatedObject> annotatedParameters) {
      this.traverseInterfacesHierarchy(pjp, mostSpecificMethod, method -> {
         List<AnnotatedObject> annotatedParametersForActualMethod = AnnotationUtils.findAnnotatedParameters(this.annotationClass, method, pjp.getArgs());
         annotatedParameters.addAll(annotatedParametersForActualMethod);
      });
   }

   private void traverseInterfacesHierarchy(ProceedingJoinPoint pjp, Method mostSpecificMethod, Consumer<Method> consumer) {
      Class<?>[] implementedInterfaces = pjp.getThis().getClass().getInterfaces();

      for (Class<?> implementedInterface : implementedInterfaces) {
         for (Method methodFromInterface : implementedInterface.getMethods()) {
            if (this.methodsAreTheSame(mostSpecificMethod, methodFromInterface)) {
               consumer.accept(methodFromInterface);
            }
         }
      }
   }

   private List<Annotation> getMethodAnnotationsFromInterfaces(ProceedingJoinPoint pjp, Method mostSpecificMethod) {
      List<Annotation> allAnnotations = new ArrayList<>();
      this.traverseInterfacesHierarchy(
         pjp, mostSpecificMethod, method -> allAnnotations.addAll(Arrays.asList(method.getAnnotationsByType(this.annotationClass)))
      );
      return allAnnotations;
   }

   private boolean methodsAreTheSame(Method mostSpecificMethod, Method method) {
      return method.getName().equals(mostSpecificMethod.getName()) && Arrays.equals(method.getParameterTypes(), mostSpecificMethod.getParameterTypes());
   }

   private void addAnnotatedObjects(T objectToModify, List<AnnotatedObject> toBeAdded) {
      Set<String> seen = new HashSet<>();

      for (AnnotatedObject container : toBeAdded) {
         KeyValue keyValue = this.toKeyValue.apply(container.annotation, container.object);
         if (seen.add(keyValue.getKey())) {
            this.keyValueConsumer.accept(keyValue, objectToModify);
         }
      }
   }

   public Function<Class<? extends ValueResolver>, ? extends ValueResolver> getResolverProvider() {
      return this.resolverProvider;
   }

   public Function<Class<? extends ValueExpressionResolver>, ? extends ValueExpressionResolver> getExpressionResolverProvider() {
      return this.expressionResolverProvider;
   }
}
