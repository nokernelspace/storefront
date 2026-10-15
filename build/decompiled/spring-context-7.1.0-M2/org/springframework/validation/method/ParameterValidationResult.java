package org.springframework.validation.method;

import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

public class ParameterValidationResult {
   private final MethodParameter methodParameter;
   private final @Nullable Object argument;
   private final List<MessageSourceResolvable> resolvableErrors;
   private final @Nullable Object container;
   private final @Nullable Integer containerIndex;
   private final @Nullable Object containerKey;
   private final BiFunction<MessageSourceResolvable, Class<?>, Object> sourceLookup;

   public ParameterValidationResult(
      MethodParameter param,
      @Nullable Object arg,
      Collection<? extends MessageSourceResolvable> errors,
      @Nullable Object container,
      @Nullable Integer index,
      @Nullable Object key,
      BiFunction<MessageSourceResolvable, Class<?>, Object> sourceLookup
   ) {
      Assert.notNull(param, "MethodParameter is required");
      Assert.notEmpty(errors, "`resolvableErrors` must not be empty");
      this.methodParameter = param;
      this.argument = arg;
      this.resolvableErrors = List.copyOf(errors);
      this.container = container;
      this.containerIndex = index;
      this.containerKey = key;
      this.sourceLookup = sourceLookup;
   }

   public MethodParameter getMethodParameter() {
      return this.methodParameter;
   }

   public @Nullable Object getArgument() {
      return this.argument;
   }

   public List<MessageSourceResolvable> getResolvableErrors() {
      return this.resolvableErrors;
   }

   public @Nullable Object getContainer() {
      return this.container;
   }

   public @Nullable Integer getContainerIndex() {
      return this.containerIndex;
   }

   public @Nullable Object getContainerKey() {
      return this.containerKey;
   }

   public <T> T unwrap(MessageSourceResolvable error, Class<T> sourceType) {
      return (T)this.sourceLookup.apply(error, sourceType);
   }

   @Override
   public boolean equals(@Nullable Object other) {
      if (this == other) {
         return true;
      } else {
         return !super.equals(other)
            ? false
            : other instanceof ParameterValidationResult otherResult
               && this.getMethodParameter().equals(otherResult.getMethodParameter())
               && ObjectUtils.nullSafeEquals(this.getArgument(), otherResult.getArgument())
               && ObjectUtils.nullSafeEquals(this.getContainerIndex(), otherResult.getContainerIndex())
               && ObjectUtils.nullSafeEquals(this.getContainerKey(), otherResult.getContainerKey());
      }
   }

   @Override
   public int hashCode() {
      int hashCode = super.hashCode();
      hashCode = 29 * hashCode + this.getMethodParameter().hashCode();
      hashCode = 29 * hashCode + ObjectUtils.nullSafeHashCode(this.getArgument());
      hashCode = 29 * hashCode + ObjectUtils.nullSafeHashCode(this.getContainerIndex());
      return 29 * hashCode + ObjectUtils.nullSafeHashCode(this.getContainerKey());
   }

   @Override
   public String toString() {
      return this.getClass().getSimpleName()
         + " for "
         + this.methodParameter
         + ", argument value '"
         + ObjectUtils.nullSafeConciseToString(this.argument)
         + "',"
         + (this.containerIndex != null ? "containerIndex[" + this.containerIndex + "]," : "")
         + (this.containerKey != null ? "containerKey['" + this.containerKey + "']," : "")
         + " errors: "
         + this.getResolvableErrors();
   }
}
