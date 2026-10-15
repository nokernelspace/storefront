package org.springframework.aot.hint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class LambdaHint implements ConditionalHint {
   private final TypeReference declaringClass;
   private final @Nullable TypeReference reachableType;
   private final LambdaHint.@Nullable DeclaringMethod declaringMethod;
   private final List<TypeReference> interfaces;

   private LambdaHint(LambdaHint.Builder builder) {
      this.declaringClass = builder.declaringClass;
      this.reachableType = builder.reachableType;
      this.declaringMethod = builder.declaringMethod;
      this.interfaces = List.copyOf(builder.interfaces);
   }

   public static LambdaHint.Builder of(TypeReference declaringClass) {
      return new LambdaHint.Builder(declaringClass);
   }

   public static LambdaHint.Builder of(Class<?> declaringClass) {
      return new LambdaHint.Builder(TypeReference.of(declaringClass));
   }

   public TypeReference getDeclaringClass() {
      return this.declaringClass;
   }

   @Override
   public @Nullable TypeReference getReachableType() {
      return this.reachableType;
   }

   public LambdaHint.@Nullable DeclaringMethod getDeclaringMethod() {
      return this.declaringMethod;
   }

   public List<TypeReference> getInterfaces() {
      return this.interfaces;
   }

   public static class Builder {
      private final TypeReference declaringClass;
      private @Nullable TypeReference reachableType;
      private LambdaHint.@Nullable DeclaringMethod declaringMethod;
      private final List<TypeReference> interfaces = new ArrayList<>();

      Builder(TypeReference declaringClass) {
         this.declaringClass = declaringClass;
      }

      public LambdaHint.Builder onReachableType(TypeReference reachableType) {
         this.reachableType = reachableType;
         return this;
      }

      public LambdaHint.Builder onReachableType(Class<?> reachableType) {
         this.reachableType = TypeReference.of(reachableType);
         return this;
      }

      public LambdaHint.Builder withDeclaringMethod(String name, List<TypeReference> parameterTypes) {
         this.declaringMethod = new LambdaHint.DeclaringMethod(name, parameterTypes);
         return this;
      }

      public LambdaHint.Builder withDeclaringMethod(String name, Class<?>... parameterTypes) {
         return this.withDeclaringMethod(name, Arrays.stream(parameterTypes).map(TypeReference::of).toList());
      }

      public LambdaHint.Builder withInterfaces(TypeReference... interfaces) {
         this.interfaces.addAll(Arrays.asList(interfaces));
         return this;
      }

      public LambdaHint.Builder withInterfaces(Class<?>... interfaces) {
         this.interfaces.addAll(Arrays.stream(interfaces).map(TypeReference::of).toList());
         return this;
      }

      public LambdaHint build() {
         return new LambdaHint(this);
      }
   }

   public record DeclaringMethod(String name, List<TypeReference> parameterTypes) {
   }
}
