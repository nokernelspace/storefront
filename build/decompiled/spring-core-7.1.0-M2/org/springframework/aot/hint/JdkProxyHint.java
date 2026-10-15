package org.springframework.aot.hint;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class JdkProxyHint implements ConditionalHint {
   private final List<TypeReference> proxiedInterfaces;
   private final @Nullable TypeReference reachableType;
   private final boolean javaSerialization;

   private JdkProxyHint(JdkProxyHint.Builder builder) {
      this.proxiedInterfaces = List.copyOf(builder.proxiedInterfaces);
      this.reachableType = builder.reachableType;
      this.javaSerialization = builder.javaSerialization;
   }

   public static JdkProxyHint.Builder of(TypeReference... proxiedInterfaces) {
      return new JdkProxyHint.Builder().proxiedInterfaces(proxiedInterfaces);
   }

   public static JdkProxyHint.Builder of(Class<?>... proxiedInterfaces) {
      return new JdkProxyHint.Builder().proxiedInterfaces(proxiedInterfaces);
   }

   public List<TypeReference> getProxiedInterfaces() {
      return this.proxiedInterfaces;
   }

   @Override
   public @Nullable TypeReference getReachableType() {
      return this.reachableType;
   }

   public boolean hasJavaSerialization() {
      return this.javaSerialization;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof JdkProxyHint that
            && this.proxiedInterfaces.equals(that.proxiedInterfaces)
            && Objects.equals(this.reachableType, that.reachableType)
            && Objects.equals(this.javaSerialization, that.javaSerialization);
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.proxiedInterfaces);
   }

   public static class Builder {
      private final LinkedList<TypeReference> proxiedInterfaces = new LinkedList<>();
      private @Nullable TypeReference reachableType;
      private boolean javaSerialization;

      Builder() {
      }

      public JdkProxyHint.Builder proxiedInterfaces(TypeReference... proxiedInterfaces) {
         this.proxiedInterfaces.addAll(Arrays.asList(proxiedInterfaces));
         return this;
      }

      public JdkProxyHint.Builder proxiedInterfaces(Class<?>... proxiedInterfaces) {
         this.proxiedInterfaces.addAll(toTypeReferences(proxiedInterfaces));
         return this;
      }

      public JdkProxyHint.Builder onReachableType(TypeReference reachableType) {
         this.reachableType = reachableType;
         return this;
      }

      public JdkProxyHint.Builder withJavaSerialization(boolean javaSerialization) {
         this.javaSerialization = javaSerialization;
         return this;
      }

      JdkProxyHint build() {
         return new JdkProxyHint(this);
      }

      private static List<TypeReference> toTypeReferences(Class<?>... proxiedInterfaces) {
         List<String> invalidTypes = Arrays.stream(proxiedInterfaces)
            .filter(candidate -> !candidate.isInterface() || candidate.isSealed())
            .map(Class::getName)
            .toList();
         if (!invalidTypes.isEmpty()) {
            throw new IllegalArgumentException("The following must be non-sealed interfaces: " + invalidTypes);
         } else {
            return TypeReference.listOf(proxiedInterfaces);
         }
      }
   }
}
