package org.springframework.aot.generate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.javapoet.CodeBlock;
import org.springframework.util.Assert;

public final class ValueCodeGenerator {
   private static final ValueCodeGenerator INSTANCE = new ValueCodeGenerator(ValueCodeGeneratorDelegates.INSTANCES, null);
   private static final CodeBlock NULL_VALUE_CODE_BLOCK = CodeBlock.of("null");
   private final List<ValueCodeGenerator.Delegate> delegates;
   private final @Nullable GeneratedMethods generatedMethods;

   private ValueCodeGenerator(List<ValueCodeGenerator.Delegate> delegates, @Nullable GeneratedMethods generatedMethods) {
      this.delegates = delegates;
      this.generatedMethods = generatedMethods;
   }

   public static ValueCodeGenerator withDefaults() {
      return INSTANCE;
   }

   public static ValueCodeGenerator with(ValueCodeGenerator.Delegate... delegates) {
      return with(Arrays.asList(delegates));
   }

   public static ValueCodeGenerator with(List<ValueCodeGenerator.Delegate> delegates) {
      Assert.notEmpty(delegates, "Delegates must not be empty");
      return new ValueCodeGenerator(new ArrayList<>(delegates), null);
   }

   public ValueCodeGenerator add(List<ValueCodeGenerator.Delegate> additionalDelegates) {
      Assert.notEmpty(additionalDelegates, "AdditionalDelegates must not be empty");
      List<ValueCodeGenerator.Delegate> allDelegates = new ArrayList<>(this.delegates);
      allDelegates.addAll(additionalDelegates);
      return new ValueCodeGenerator(allDelegates, this.generatedMethods);
   }

   public ValueCodeGenerator scoped(GeneratedMethods generatedMethods) {
      return new ValueCodeGenerator(this.delegates, generatedMethods);
   }

   public CodeBlock generateCode(@Nullable Object value) {
      if (value == null) {
         return NULL_VALUE_CODE_BLOCK;
      }

      try {
         for (ValueCodeGenerator.Delegate delegate : this.delegates) {
            CodeBlock code = delegate.generateCode(this, value);
            if (code != null) {
               return code;
            }
         }

         throw new UnsupportedTypeValueCodeGenerationException(value);
      } catch (Exception ex) {
         throw new ValueCodeGenerationException(value, ex);
      }
   }

   public @Nullable GeneratedMethods getGeneratedMethods() {
      return this.generatedMethods;
   }

   public interface Delegate {
      @Nullable CodeBlock generateCode(ValueCodeGenerator valueCodeGenerator, Object value);
   }
}
