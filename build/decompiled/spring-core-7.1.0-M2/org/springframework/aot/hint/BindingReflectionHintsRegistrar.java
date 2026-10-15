package org.springframework.aot.hint;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KClass;
import org.jspecify.annotations.Nullable;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

public class BindingReflectionHintsRegistrar {
   private static final String KOTLIN_COMPANION_SUFFIX = "$Companion";
   private static final String JACKSON_ANNOTATION = "com.fasterxml.jackson.annotation.JacksonAnnotation";
   private static final boolean JACKSON_ANNOTATION_PRESENT = ClassUtils.isPresent(
      "com.fasterxml.jackson.annotation.JacksonAnnotation", BindingReflectionHintsRegistrar.class.getClassLoader()
   );

   public void registerReflectionHints(ReflectionHints hints, Type... types) {
      Set<Type> seen = new HashSet<>();

      for (Type type : types) {
         this.registerReflectionHints(hints, seen, type);
      }
   }

   private boolean shouldSkipType(Class<?> type) {
      return type.isPrimitive() || type == Object.class;
   }

   private boolean shouldSkipMembers(Class<?> type) {
      return type.getCanonicalName().startsWith("java.") || type.isArray();
   }

   private void registerReflectionHints(ReflectionHints hints, Set<Type> seen, Type type) {
      if (!seen.contains(type)) {
         seen.add(type);
         if (type instanceof Class<?> clazz) {
            if (this.shouldSkipType(clazz)) {
               return;
            }

            hints.registerType(
               clazz,
               typeHint -> {
                  if (!this.shouldSkipMembers(clazz)) {
                     if (clazz.isRecord()) {
                        typeHint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);

                        for (RecordComponent recordComponent : clazz.getRecordComponents()) {
                           this.registerRecordHints(hints, seen, recordComponent.getAccessor());
                        }
                     }

                     if (clazz.isEnum()) {
                        typeHint.withMembers(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS);
                     }

                     typeHint.withMembers(MemberCategory.ACCESS_DECLARED_FIELDS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);

                     for (Method method : clazz.getMethods()) {
                        String methodName = method.getName();
                        if (methodName.startsWith("set") && method.getParameterCount() == 1) {
                           this.registerPropertyHints(hints, seen, method, 0);
                        } else if (methodName.startsWith("get") && method.getParameterCount() == 0 && method.getReturnType() != void.class
                           || methodName.startsWith("is")
                              && method.getParameterCount() == 0
                              && ClassUtils.resolvePrimitiveIfNecessary(method.getReturnType()) == Boolean.class) {
                           this.registerPropertyHints(hints, seen, method, -1);
                        }
                     }

                     if (JACKSON_ANNOTATION_PRESENT) {
                        this.registerJacksonHints(hints, clazz);
                     }

                     this.registerObjectToObjectConverterHints(hints, clazz);
                  }

                  if (KotlinDetector.isKotlinType(clazz)) {
                     BindingReflectionHintsRegistrar.KotlinDelegate.registerComponentHints(hints, clazz);
                     this.registerKotlinSerializationHints(hints, clazz);
                  }
               }
            );
         }

         Set<Class<?>> referencedTypes = new LinkedHashSet<>();
         this.collectReferencedTypes(referencedTypes, ResolvableType.forType(type));
         referencedTypes.forEach(referencedType -> this.registerReflectionHints(hints, seen, referencedType));
      }
   }

   private void registerRecordHints(ReflectionHints hints, Set<Type> seen, Method method) {
      hints.registerMethod(method, ExecutableMode.INVOKE);
      MethodParameter methodParameter = MethodParameter.forExecutable(method, -1);
      Type methodParameterType = methodParameter.getGenericParameterType();
      this.registerReflectionHints(hints, seen, methodParameterType);
   }

   private void registerPropertyHints(ReflectionHints hints, Set<Type> seen, @Nullable Method method, int parameterIndex) {
      if (method != null && method.getDeclaringClass() != Object.class && method.getDeclaringClass() != Enum.class) {
         hints.registerMethod(method, ExecutableMode.INVOKE);
         MethodParameter methodParameter = MethodParameter.forExecutable(method, parameterIndex);
         Type methodParameterType = methodParameter.getGenericParameterType();
         this.registerReflectionHints(hints, seen, methodParameterType);
      }
   }

   private void registerKotlinSerializationHints(ReflectionHints hints, Class<?> clazz) {
      String companionClassName = clazz.getCanonicalName() + "$Companion";
      if (ClassUtils.isPresent(companionClassName, null)) {
         Class<?> companionClass = ClassUtils.resolveClassName(companionClassName, null);
         Method serializerMethod = ClassUtils.getMethodIfAvailable(companionClass, "serializer", (Class<?>[])null);
         if (serializerMethod != null) {
            hints.registerMethod(serializerMethod, ExecutableMode.INVOKE);
         }
      }
   }

   private void registerObjectToObjectConverterHints(ReflectionHints hints, Class<?> clazz) {
      for (Method method : clazz.getMethods()) {
         String name = method.getName();
         boolean isStatic = Modifier.isStatic(method.getModifiers());
         if (isStatic
            && clazz != String.class
            && areRelatedTypes(method.getReturnType(), clazz)
            && (name.equals("valueOf") || name.equals("of") || name.equals("from"))) {
            hints.registerMethod(method, ExecutableMode.INVOKE);
         }

         if (!isStatic && method.getReturnType() != String.class && name.equals("to" + method.getReturnType().getSimpleName())) {
            hints.registerMethod(method, ExecutableMode.INVOKE);
         }
      }
   }

   private static boolean areRelatedTypes(Class<?> type1, Class<?> type2) {
      return ClassUtils.isAssignable(type1, type2) || ClassUtils.isAssignable(type2, type1);
   }

   private void collectReferencedTypes(Set<Class<?>> types, ResolvableType resolvableType) {
      Class<?> clazz = resolvableType.resolve();
      if (clazz != null && !types.contains(clazz)) {
         types.add(clazz);

         for (ResolvableType genericResolvableType : resolvableType.getGenerics()) {
            this.collectReferencedTypes(types, genericResolvableType);
         }

         Class<?> superClass = clazz.getSuperclass();
         if (superClass != null && superClass != Object.class && superClass != Record.class && superClass != Enum.class) {
            types.add(superClass);
         }
      }
   }

   private void registerJacksonHints(ReflectionHints hints, Class<?> clazz) {
      ReflectionUtils.doWithFields(clazz, field -> this.forEachJacksonAnnotation(field, annotation -> {
         Field sourceField = (Field)annotation.getSource();
         if (sourceField != null) {
            hints.registerField(sourceField);
         }

         this.registerHintsForClassAttributes(hints, annotation);
      }));
      ReflectionUtils.doWithMethods(clazz, method -> this.forEachJacksonAnnotation(method, annotation -> {
         Method sourceMethod = (Method)annotation.getSource();
         if (sourceMethod != null) {
            hints.registerMethod(sourceMethod, ExecutableMode.INVOKE);
         }

         this.registerHintsForClassAttributes(hints, annotation);
      }));
      this.forEachJacksonAnnotation(clazz, annotation -> this.registerHintsForClassAttributes(hints, annotation));
   }

   private void forEachJacksonAnnotation(AnnotatedElement element, Consumer<MergedAnnotation<Annotation>> action) {
      MergedAnnotations.from(element, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY)
         .stream("com.fasterxml.jackson.annotation.JacksonAnnotation")
         .filter(MergedAnnotation::isMetaPresent)
         .forEach(action);
   }

   private void registerHintsForClassAttributes(ReflectionHints hints, MergedAnnotation<Annotation> annotation) {
      annotation.getRoot().asMap().forEach((attributeName, value) -> {
         if (value instanceof Class<?> classValue && value != Void.class) {
            if (attributeName.equals("builder")) {
               hints.registerType(classValue, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS);
            } else {
               hints.registerType(classValue, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
            }
         }
      });
   }

   private static class KotlinDelegate {
      public static void registerComponentHints(ReflectionHints hints, Class<?> type) {
         KClass<?> kClass = JvmClassMappingKt.getKotlinClass(type);
         if (kClass.isData()) {
            for (Method method : type.getMethods()) {
               String methodName = method.getName();
               if (methodName.startsWith("component") || methodName.equals("copy") || methodName.equals("copy$default")) {
                  hints.registerMethod(method, ExecutableMode.INVOKE);
               }
            }
         }
      }
   }
}
