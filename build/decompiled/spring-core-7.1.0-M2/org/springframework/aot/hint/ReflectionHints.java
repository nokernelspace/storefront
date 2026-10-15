package org.springframework.aot.hint;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

public class ReflectionHints {
   private final Map<TypeReference, TypeHint.Builder> types = new HashMap<>();
   private final Set<LambdaHint> lambdaHints = new LinkedHashSet<>();

   public Stream<TypeHint> typeHints() {
      return this.types.values().stream().map(TypeHint.Builder::build);
   }

   public Stream<LambdaHint> lambdaHints() {
      return this.lambdaHints.stream();
   }

   public @Nullable TypeHint getTypeHint(TypeReference type) {
      TypeHint.Builder typeHintBuilder = this.types.get(type);
      return typeHintBuilder != null ? typeHintBuilder.build() : null;
   }

   public @Nullable TypeHint getTypeHint(Class<?> type) {
      return this.getTypeHint(TypeReference.of(type));
   }

   public ReflectionHints registerType(TypeReference type, Consumer<TypeHint.Builder> typeHint) {
      TypeHint.Builder builder = this.types.computeIfAbsent(type, TypeHint.Builder::new);
      typeHint.accept(builder);
      return this;
   }

   public ReflectionHints registerType(TypeReference type, MemberCategory... memberCategories) {
      return this.registerType(type, TypeHint.builtWith(memberCategories));
   }

   public ReflectionHints registerType(Class<?> type, Consumer<TypeHint.Builder> typeHint) {
      Assert.notNull(type, "'type' must not be null");
      if (type.getCanonicalName() != null) {
         this.registerType(TypeReference.of(type), typeHint);
      }

      return this;
   }

   public ReflectionHints registerType(Class<?> type, MemberCategory... memberCategories) {
      Assert.notNull(type, "'type' must not be null");
      if (type.getCanonicalName() != null) {
         this.registerType(TypeReference.of(type), memberCategories);
      }

      return this;
   }

   public ReflectionHints registerTypeIfPresent(@Nullable ClassLoader classLoader, String typeName, Consumer<TypeHint.Builder> typeHint) {
      if (ClassUtils.isPresent(typeName, classLoader)) {
         this.registerType(TypeReference.of(typeName), typeHint);
      }

      return this;
   }

   public ReflectionHints registerTypeIfPresent(@Nullable ClassLoader classLoader, String typeName, MemberCategory... memberCategories) {
      return this.registerTypeIfPresent(classLoader, typeName, TypeHint.builtWith(memberCategories));
   }

   public ReflectionHints registerTypes(Iterable<TypeReference> types, Consumer<TypeHint.Builder> typeHint) {
      types.forEach(type -> this.registerType(type, typeHint));
      return this;
   }

   public ReflectionHints registerForInterfaces(Class<?> type, Consumer<TypeHint.Builder> typeHint) {
      for (Class<?> currentClass = type; currentClass != null && currentClass != Object.class; currentClass = currentClass.getSuperclass()) {
         for (Class<?> interfaceType : currentClass.getInterfaces()) {
            if (!ClassUtils.isJavaLanguageInterface(interfaceType)) {
               this.registerType(interfaceType, typeHint);
               this.registerForInterfaces(interfaceType, typeHint);
            }
         }
      }

      return this;
   }

   public ReflectionHints registerField(Field field) {
      return this.registerType(TypeReference.of(field.getDeclaringClass()), typeHint -> typeHint.withField(field.getName()));
   }

   public ReflectionHints registerConstructor(Constructor<?> constructor, ExecutableMode mode) {
      return this.registerType(TypeReference.of(constructor.getDeclaringClass()), typeHint -> typeHint.withConstructor(this.mapParameters(constructor), mode));
   }

   public ReflectionHints registerMethod(Method method, ExecutableMode mode) {
      return this.registerType(
         TypeReference.of(method.getDeclaringClass()), typeHint -> typeHint.withMethod(method.getName(), this.mapParameters(method), mode)
      );
   }

   public ReflectionHints registerJavaSerialization(Class<?> type) {
      return this.registerType(TypeReference.of(type), typeHint -> typeHint.withJavaSerialization(true));
   }

   public ReflectionHints registerLambda(TypeReference declaringClass, Consumer<LambdaHint.Builder> lambdaHint) {
      LambdaHint.Builder builder = LambdaHint.of(declaringClass);
      lambdaHint.accept(builder);
      this.lambdaHints.add(builder.build());
      return this;
   }

   public ReflectionHints registerLambda(Class<?> declaringClass, Consumer<LambdaHint.Builder> lambdaHint) {
      return this.registerLambda(TypeReference.of(declaringClass), lambdaHint);
   }

   private List<TypeReference> mapParameters(Executable executable) {
      return TypeReference.listOf(executable.getParameterTypes());
   }
}
