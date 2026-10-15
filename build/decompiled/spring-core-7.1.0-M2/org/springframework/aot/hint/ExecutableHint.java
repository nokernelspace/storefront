package org.springframework.aot.hint;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public final class ExecutableHint extends MemberHint implements Comparable<ExecutableHint> {
   private final List<TypeReference> parameterTypes;
   private final ExecutableMode mode;

   private ExecutableHint(ExecutableHint.Builder builder) {
      super(builder.name);
      this.parameterTypes = List.copyOf(builder.parameterTypes);
      this.mode = builder.mode != null ? builder.mode : ExecutableMode.INVOKE;
   }

   static ExecutableHint.Builder ofConstructor(List<TypeReference> parameterTypes) {
      return new ExecutableHint.Builder("<init>", parameterTypes);
   }

   static ExecutableHint.Builder ofMethod(String name, List<TypeReference> parameterTypes) {
      return new ExecutableHint.Builder(name, parameterTypes);
   }

   public List<TypeReference> getParameterTypes() {
      return this.parameterTypes;
   }

   public ExecutableMode getMode() {
      return this.mode;
   }

   public static Consumer<ExecutableHint.Builder> builtWith(ExecutableMode mode) {
      return builder -> builder.withMode(mode);
   }

   public int compareTo(ExecutableHint other) {
      return Comparator.comparing(MemberHint::getName, String::compareToIgnoreCase)
         .thenComparing(ExecutableHint::getParameterTypes, Comparator.comparingInt(List::size))
         .thenComparing(ExecutableHint::getParameterTypes, (params1, params2) -> {
            String left = params1.stream().map(TypeReference::getCanonicalName).collect(Collectors.joining(","));
            String right = params2.stream().map(TypeReference::getCanonicalName).collect(Collectors.joining(","));
            return left.compareTo(right);
         })
         .compare(this, other);
   }

   public static class Builder {
      private final String name;
      private final List<TypeReference> parameterTypes;
      private @Nullable ExecutableMode mode;

      Builder(String name, List<TypeReference> parameterTypes) {
         this.name = name;
         this.parameterTypes = parameterTypes;
      }

      public ExecutableHint.Builder withMode(ExecutableMode mode) {
         Assert.notNull(mode, "'mode' must not be null");
         if (this.mode == null || !this.mode.includes(mode)) {
            this.mode = mode;
         }

         return this;
      }

      ExecutableHint build() {
         return new ExecutableHint(this);
      }
   }
}
