package org.springframework.aot.hint.predicate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.ExecutableHint;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeHint;
import org.springframework.aot.hint.TypeReference;
import org.springframework.core.MethodIntrospector;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

public class ReflectionHintsPredicates {
   ReflectionHintsPredicates() {
   }

   public ReflectionHintsPredicates.TypeHintPredicate onType(TypeReference typeReference) {
      Assert.notNull(typeReference, "'typeReference' must not be null");
      return new ReflectionHintsPredicates.TypeHintPredicate(typeReference);
   }

   public ReflectionHintsPredicates.TypeHintPredicate onType(Class<?> type) {
      Assert.notNull(type, "'type' must not be null");
      return new ReflectionHintsPredicates.TypeHintPredicate(TypeReference.of(type));
   }

   @Deprecated(since = "7.0", forRemoval = true)
   public ReflectionHintsPredicates.ConstructorHintPredicate onConstructor(Constructor<?> constructor) {
      Assert.notNull(constructor, "'constructor' must not be null");
      return new ReflectionHintsPredicates.ConstructorHintPredicate(constructor);
   }

   public Predicate<RuntimeHints> onConstructorInvocation(Constructor<?> constructor) {
      Assert.notNull(constructor, "'constructor' must not be null");
      return new ReflectionHintsPredicates.ConstructorHintPredicate(constructor).invoke();
   }

   @Deprecated(since = "7.0", forRemoval = true)
   public ReflectionHintsPredicates.MethodHintPredicate onMethod(Method method) {
      Assert.notNull(method, "'method' must not be null");
      return new ReflectionHintsPredicates.MethodHintPredicate(method);
   }

   public Predicate<RuntimeHints> onMethodInvocation(Method method) {
      Assert.notNull(method, "'method' must not be null");
      return new ReflectionHintsPredicates.MethodHintPredicate(method).invoke();
   }

   @Deprecated(since = "7.0", forRemoval = true)
   public ReflectionHintsPredicates.MethodHintPredicate onMethod(Class<?> type, String methodName) {
      Assert.notNull(type, "'type' must not be null");
      Assert.hasText(methodName, "'methodName' must not be empty");
      return new ReflectionHintsPredicates.MethodHintPredicate(this.getMethod(type, methodName));
   }

   public Predicate<RuntimeHints> onMethodInvocation(Class<?> type, String methodName) {
      Assert.notNull(type, "'type' must not be null");
      Assert.hasText(methodName, "'methodName' must not be empty");
      return new ReflectionHintsPredicates.MethodHintPredicate(this.getMethod(type, methodName)).invoke();
   }

   @Deprecated(since = "7.0", forRemoval = true)
   public ReflectionHintsPredicates.MethodHintPredicate onMethod(String className, String methodName) throws ClassNotFoundException {
      Assert.hasText(className, "'className' must not be empty");
      Assert.hasText(methodName, "'methodName' must not be empty");
      return this.onMethod(Class.forName(className), methodName);
   }

   public Predicate<RuntimeHints> onMethodInvocation(String className, String methodName) throws ClassNotFoundException {
      Assert.hasText(className, "'className' must not be empty");
      Assert.hasText(methodName, "'methodName' must not be empty");
      return this.onMethod(Class.forName(className), methodName).invoke();
   }

   private Method getMethod(Class<?> type, String methodName) {
      ReflectionUtils.MethodFilter selector = method -> methodName.equals(method.getName());
      Set<Method> methods = MethodIntrospector.selectMethods(type, selector);
      if (methods.size() == 1) {
         return methods.iterator().next();
      } else if (methods.size() > 1) {
         throw new IllegalArgumentException("Found multiple methods named '%s' on class %s".formatted(methodName, type.getName()));
      } else {
         throw new IllegalArgumentException("No method named '%s' on class %s".formatted(methodName, type.getName()));
      }
   }

   @Deprecated(since = "7.0", forRemoval = true)
   public Predicate<RuntimeHints> onField(Class<?> type, String fieldName) {
      return this.onFieldAccess(type, fieldName);
   }

   public Predicate<RuntimeHints> onFieldAccess(Class<?> type, String fieldName) {
      Assert.notNull(type, "'type' must not be null");
      Assert.hasText(fieldName, "'fieldName' must not be empty");
      Field field = ReflectionUtils.findField(type, fieldName);
      if (field == null) {
         throw new IllegalArgumentException("No field named '%s' on class %s".formatted(fieldName, type.getName()));
      } else {
         return new ReflectionHintsPredicates.FieldHintPredicate(field);
      }
   }

   @Deprecated(since = "7.0", forRemoval = true)
   public Predicate<RuntimeHints> onField(String className, String fieldName) throws ClassNotFoundException {
      return this.onFieldAccess(className, fieldName);
   }

   public Predicate<RuntimeHints> onFieldAccess(String className, String fieldName) throws ClassNotFoundException {
      Assert.hasText(className, "'className' must not be empty");
      Assert.hasText(fieldName, "'fieldName' must not be empty");
      return this.onFieldAccess(Class.forName(className), fieldName);
   }

   @Deprecated(since = "7.0", forRemoval = true)
   public Predicate<RuntimeHints> onField(Field field) {
      return this.onFieldAccess(field);
   }

   public Predicate<RuntimeHints> onFieldAccess(Field field) {
      Assert.notNull(field, "'field' must not be null");
      return new ReflectionHintsPredicates.FieldHintPredicate(field);
   }

   public Predicate<RuntimeHints> onJavaSerialization(Class<?> type, boolean javaSerialization) {
      Assert.notNull(type, "'type' must not be null");
      return new ReflectionHintsPredicates.JavaSerializationHintPredicate(TypeReference.of(type), javaSerialization);
   }

   public Predicate<RuntimeHints> onJavaSerialization(TypeReference typeReference, boolean javaSerialization) {
      Assert.notNull(typeReference, "'typeReference' must not be null");
      return new ReflectionHintsPredicates.JavaSerializationHintPredicate(typeReference, javaSerialization);
   }

   @Deprecated(since = "7.0", forRemoval = true)
   public static class ConstructorHintPredicate extends ReflectionHintsPredicates.ExecutableHintPredicate<Constructor<?>> {
      ConstructorHintPredicate(Constructor<?> constructor) {
         super(constructor);
      }

      public boolean test(RuntimeHints runtimeHints) {
         return new ReflectionHintsPredicates.TypeHintPredicate(TypeReference.of(this.executable.getDeclaringClass()))
            .and(hints -> this.executableMode == ExecutableMode.INTROSPECT)
            .or(
               new ReflectionHintsPredicates.TypeHintPredicate(TypeReference.of(this.executable.getDeclaringClass()))
                  .withMemberCategory(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS)
                  .and(hints -> Modifier.isPublic(this.executable.getModifiers()))
                  .and(hints -> this.executableMode == ExecutableMode.INVOKE)
            )
            .or(
               new ReflectionHintsPredicates.TypeHintPredicate(TypeReference.of(this.executable.getDeclaringClass()))
                  .withMemberCategory(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)
                  .and(hints -> this.executableMode == ExecutableMode.INVOKE)
            )
            .or(this.exactMatch())
            .test(runtimeHints);
      }

      @Override
      Predicate<RuntimeHints> exactMatch() {
         return hints -> {
            TypeHint hint = hints.reflection().getTypeHint(this.executable.getDeclaringClass());
            return hint != null && hint.constructors().anyMatch(executableHint -> {
               List<TypeReference> parameters = TypeReference.listOf(this.executable.getParameterTypes());
               return includes(executableHint, "<init>", parameters, this.executableMode);
            });
         };
      }
   }

   @Deprecated(since = "7.0", forRemoval = true)
   public abstract static class ExecutableHintPredicate<T extends Executable> implements Predicate<RuntimeHints> {
      protected final T executable;
      protected ExecutableMode executableMode = ExecutableMode.INTROSPECT;

      ExecutableHintPredicate(T executable) {
         this.executable = executable;
      }

      public ReflectionHintsPredicates.ExecutableHintPredicate<T> introspect() {
         this.executableMode = ExecutableMode.INTROSPECT;
         return this;
      }

      public ReflectionHintsPredicates.ExecutableHintPredicate<T> invoke() {
         this.executableMode = ExecutableMode.INVOKE;
         return this;
      }

      abstract Predicate<RuntimeHints> exactMatch();

      static boolean includes(ExecutableHint hint, String name, List<TypeReference> parameterTypes, ExecutableMode executableModes) {
         return hint.getName().equals(name)
            && hint.getParameterTypes().equals(parameterTypes)
            && (hint.getMode().equals(ExecutableMode.INVOKE) || !executableModes.equals(ExecutableMode.INVOKE));
      }
   }

   @Deprecated(since = "7.0", forRemoval = true)
   public static class FieldHintPredicate implements Predicate<RuntimeHints> {
      private final Field field;

      FieldHintPredicate(Field field) {
         this.field = field;
      }

      public boolean test(RuntimeHints runtimeHints) {
         TypeHint typeHint = runtimeHints.reflection().getTypeHint(this.field.getDeclaringClass());
         return typeHint == null ? false : this.memberCategoryMatch(typeHint) || this.exactMatch(typeHint);
      }

      private boolean memberCategoryMatch(TypeHint typeHint) {
         return Modifier.isPublic(this.field.getModifiers())
            ? typeHint.getMemberCategories().contains(MemberCategory.ACCESS_PUBLIC_FIELDS)
               || typeHint.getMemberCategories().contains(MemberCategory.PUBLIC_FIELDS)
            : typeHint.getMemberCategories().contains(MemberCategory.ACCESS_DECLARED_FIELDS)
               || typeHint.getMemberCategories().contains(MemberCategory.DECLARED_FIELDS);
      }

      private boolean exactMatch(TypeHint typeHint) {
         return typeHint.fields().anyMatch(fieldHint -> this.field.getName().equals(fieldHint.getName()));
      }
   }

   public static class JavaSerializationHintPredicate implements Predicate<RuntimeHints> {
      private final TypeReference typeReference;
      private final boolean javaSerialization;

      JavaSerializationHintPredicate(TypeReference typeReference, boolean javaSerialization) {
         this.typeReference = typeReference;
         this.javaSerialization = javaSerialization;
      }

      public boolean test(RuntimeHints runtimeHints) {
         TypeHint typeHint = runtimeHints.reflection().getTypeHint(this.typeReference);
         return typeHint == null ? false : typeHint.hasJavaSerialization() == this.javaSerialization;
      }
   }

   @Deprecated(since = "7.0", forRemoval = true)
   public static class MethodHintPredicate extends ReflectionHintsPredicates.ExecutableHintPredicate<Method> {
      MethodHintPredicate(Method method) {
         super(method);
      }

      public boolean test(RuntimeHints runtimeHints) {
         return new ReflectionHintsPredicates.TypeHintPredicate(TypeReference.of(this.executable.getDeclaringClass()))
            .and(hints -> this.executableMode == ExecutableMode.INTROSPECT)
            .or(
               new ReflectionHintsPredicates.TypeHintPredicate(TypeReference.of(this.executable.getDeclaringClass()))
                  .withMemberCategory(MemberCategory.INVOKE_PUBLIC_METHODS)
                  .and(hints -> Modifier.isPublic(this.executable.getModifiers()))
                  .and(hints -> this.executableMode == ExecutableMode.INVOKE)
            )
            .or(
               new ReflectionHintsPredicates.TypeHintPredicate(TypeReference.of(this.executable.getDeclaringClass()))
                  .withMemberCategory(MemberCategory.INVOKE_DECLARED_METHODS)
                  .and(hints -> !Modifier.isPublic(this.executable.getModifiers()))
                  .and(hints -> this.executableMode == ExecutableMode.INVOKE)
            )
            .or(this.exactMatch())
            .test(runtimeHints);
      }

      @Override
      Predicate<RuntimeHints> exactMatch() {
         return hints -> {
            TypeHint hint = hints.reflection().getTypeHint(this.executable.getDeclaringClass());
            return hint != null && hint.methods().anyMatch(executableHint -> {
               List<TypeReference> parameters = TypeReference.listOf(this.executable.getParameterTypes());
               return includes(executableHint, this.executable.getName(), parameters, this.executableMode);
            });
         };
      }
   }

   public static class TypeHintPredicate implements Predicate<RuntimeHints> {
      private final TypeReference type;

      TypeHintPredicate(TypeReference type) {
         this.type = type;
      }

      private @Nullable TypeHint getTypeHint(RuntimeHints hints) {
         return hints.reflection().getTypeHint(this.type);
      }

      public boolean test(RuntimeHints hints) {
         return this.getTypeHint(hints) != null;
      }

      public Predicate<RuntimeHints> withMemberCategory(MemberCategory memberCategory) {
         Assert.notNull(memberCategory, "'memberCategory' must not be null");
         return this.and(hints -> {
            TypeHint hint = this.getTypeHint(hints);
            return hint != null && hint.getMemberCategories().contains(memberCategory);
         });
      }

      public Predicate<RuntimeHints> withMemberCategories(MemberCategory... memberCategories) {
         Assert.notEmpty(memberCategories, "'memberCategories' must not be empty");
         return this.and(hints -> {
            TypeHint hint = this.getTypeHint(hints);
            return hint != null && hint.getMemberCategories().containsAll(Arrays.asList(memberCategories));
         });
      }

      public Predicate<RuntimeHints> withAnyMemberCategory(MemberCategory... memberCategories) {
         Assert.notEmpty(memberCategories, "'memberCategories' must not be empty");
         return this.and(hints -> {
            TypeHint hint = this.getTypeHint(hints);
            return hint != null && Arrays.stream(memberCategories).anyMatch(memberCategory -> hint.getMemberCategories().contains(memberCategory));
         });
      }
   }
}
