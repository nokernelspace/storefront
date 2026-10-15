package org.springframework.aot.hint;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public final class TypeHint implements ConditionalHint {
   private final TypeReference type;
   private final @Nullable TypeReference reachableType;
   private final Set<FieldHint> fields;
   private final Set<ExecutableHint> constructors;
   private final Set<ExecutableHint> methods;
   private final Set<MemberCategory> memberCategories;
   private final boolean javaSerialization;

   private TypeHint(TypeHint.Builder builder) {
      this.type = builder.type;
      this.reachableType = builder.reachableType;
      this.memberCategories = Set.copyOf(builder.memberCategories);
      this.fields = builder.fields.stream().map(FieldHint::new).collect(Collectors.toSet());
      this.constructors = builder.constructors.values().stream().map(ExecutableHint.Builder::build).collect(Collectors.toSet());
      this.methods = builder.methods.values().stream().map(ExecutableHint.Builder::build).collect(Collectors.toSet());
      this.javaSerialization = builder.javaSerialization;
   }

   static TypeHint.Builder of(TypeReference type) {
      Assert.notNull(type, "'type' must not be null");
      return new TypeHint.Builder(type);
   }

   public TypeReference getType() {
      return this.type;
   }

   @Override
   public @Nullable TypeReference getReachableType() {
      return this.reachableType;
   }

   public Stream<FieldHint> fields() {
      return this.fields.stream();
   }

   public Stream<ExecutableHint> constructors() {
      return this.constructors.stream();
   }

   public Stream<ExecutableHint> methods() {
      return this.methods.stream();
   }

   public Set<MemberCategory> getMemberCategories() {
      return this.memberCategories;
   }

   public boolean hasJavaSerialization() {
      return this.javaSerialization;
   }

   @Override
   public String toString() {
      return TypeHint.class.getSimpleName() + "[type=" + this.type + "]";
   }

   public static Consumer<TypeHint.Builder> builtWith(MemberCategory... memberCategories) {
      return builder -> builder.withMembers(memberCategories);
   }

   public static class Builder {
      private final TypeReference type;
      private @Nullable TypeReference reachableType;
      private final Set<String> fields = new HashSet<>();
      private final Map<TypeHint.ExecutableKey, ExecutableHint.Builder> constructors = new HashMap<>();
      private final Map<TypeHint.ExecutableKey, ExecutableHint.Builder> methods = new HashMap<>();
      private final Set<MemberCategory> memberCategories = new HashSet<>();
      private boolean javaSerialization;

      Builder(TypeReference type) {
         this.type = type;
      }

      public TypeHint.Builder onReachableType(TypeReference reachableType) {
         this.reachableType = reachableType;
         return this;
      }

      public TypeHint.Builder onReachableType(Class<?> reachableType) {
         this.reachableType = TypeReference.of(reachableType);
         return this;
      }

      public TypeHint.Builder withField(String name) {
         this.fields.add(name);
         return this;
      }

      public TypeHint.Builder withConstructor(List<TypeReference> parameterTypes, ExecutableMode mode) {
         return this.withConstructor(parameterTypes, ExecutableHint.builtWith(mode));
      }

      private TypeHint.Builder withConstructor(List<TypeReference> parameterTypes, Consumer<ExecutableHint.Builder> constructorHint) {
         TypeHint.ExecutableKey key = new TypeHint.ExecutableKey("<init>", parameterTypes);
         ExecutableHint.Builder builder = this.constructors.computeIfAbsent(key, k -> ExecutableHint.ofConstructor(parameterTypes));
         constructorHint.accept(builder);
         return this;
      }

      public TypeHint.Builder withMethod(String name, List<TypeReference> parameterTypes, ExecutableMode mode) {
         return this.withMethod(name, parameterTypes, ExecutableHint.builtWith(mode));
      }

      private TypeHint.Builder withMethod(String name, List<TypeReference> parameterTypes, Consumer<ExecutableHint.Builder> methodHint) {
         TypeHint.ExecutableKey key = new TypeHint.ExecutableKey(name, parameterTypes);
         ExecutableHint.Builder builder = this.methods.computeIfAbsent(key, k -> ExecutableHint.ofMethod(name, parameterTypes));
         methodHint.accept(builder);
         return this;
      }

      public TypeHint.Builder withMembers(MemberCategory... memberCategories) {
         this.memberCategories.addAll(Arrays.asList(memberCategories));
         return this;
      }

      public TypeHint.Builder withJavaSerialization(boolean javaSerialization) {
         this.javaSerialization = javaSerialization;
         return this;
      }

      TypeHint build() {
         return new TypeHint(this);
      }
   }

   private static final class ExecutableKey {
      private final String name;
      private final List<String> parameterTypes;

      private ExecutableKey(String name, List<TypeReference> parameterTypes) {
         this.name = name;
         this.parameterTypes = parameterTypes.stream().map(TypeReference::getCanonicalName).toList();
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other || other instanceof TypeHint.ExecutableKey that && this.name.equals(that.name) && this.parameterTypes.equals(that.parameterTypes);
      }

      @Override
      public int hashCode() {
         return Objects.hash(this.name, this.parameterTypes);
      }
   }
}
